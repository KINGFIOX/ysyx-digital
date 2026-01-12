// soft-fp.c - Software floating-point implementation
// Based on LLVM compiler-rt builtins
// Provides software implementations for IEEE 754 double-precision operations

#include <stdint.h>
#include <stdbool.h>
#include <limits.h>

// ============================================================================
// Type definitions
// ============================================================================

typedef int32_t si_int;
typedef uint32_t su_int;
typedef int64_t di_int;
typedef uint64_t du_int;

typedef double fp_t;
typedef uint64_t rep_t;
typedef int64_t srep_t;

#define REP_C UINT64_C
#define significandBits 52

static const int typeWidth = sizeof(rep_t) * CHAR_BIT;
static const int exponentBits = typeWidth - significandBits - 1;
static const int maxExponent = (1 << exponentBits) - 1;
static const rep_t implicitBit = REP_C(1) << significandBits;
static const rep_t significandMask = implicitBit - 1;
static const rep_t signBit = REP_C(1) << (significandBits + exponentBits);
static const rep_t absMask = signBit - 1;
// static const rep_t exponentMask = absMask ^ significandMask; // FIXME: unused
// static const rep_t oneRep = ((rep_t)(maxExponent / 2)) << significandBits; // FIXME: unused
static const rep_t infRep = (rep_t)maxExponent << significandBits;
static const rep_t quietBit = implicitBit >> 1;
static const rep_t qnanRep = infRep | quietBit;

static inline rep_t toRep(fp_t x) {
    union { fp_t f; rep_t i; } u;
    u.f = x;
    return u.i;
}

static inline fp_t fromRep(rep_t x) {
    union { fp_t f; rep_t i; } u;
    u.i = x;
    return u.f;
}

static inline int normalize(rep_t *significand) {
    const int shift = __builtin_clzll(*significand) - (typeWidth - significandBits - 1);
    *significand <<= shift;
    return 1 - shift;
}

static inline void wideLeftShift(rep_t *hi, rep_t *lo, int count) {
    *hi = *hi << count | *lo >> (typeWidth - count);
    *lo = *lo << count;
}

static inline void wideRightShiftWithSticky(rep_t *hi, rep_t *lo, unsigned int count) {
    if (count < typeWidth) {
        const bool sticky = (*lo << (typeWidth - count)) != 0;
        *lo = *hi << (typeWidth - count) | *lo >> count | sticky;
        *hi = *hi >> count;
    } else if (count < 2 * typeWidth) {
        const bool sticky = *hi << (2 * typeWidth - count) | *lo;
        *lo = *hi >> (count - typeWidth) | sticky;
        *hi = 0;
    } else {
        const bool sticky = *hi | *lo;
        *lo = sticky;
        *hi = 0;
    }
}

// ============================================================================
// Comparison helpers
// ============================================================================

typedef enum {
    LE_LESS = -1,
    LE_EQUAL = 0,
    LE_GREATER = 1,
    LE_UNORDERED = 1
} LE_RESULT;

typedef enum {
    GE_LESS = -1,
    GE_EQUAL = 0,
    GE_GREATER = 1,
    GE_UNORDERED = -1
} GE_RESULT;

typedef enum {
    CMP_RESULT_LESS = -1,
    CMP_RESULT_EQUAL = 0,
    CMP_RESULT_GREATER = 1,
    CMP_RESULT_UNORDERED = 1
} CMP_RESULT;

// ============================================================================
// Double-precision addition: __adddf3
// ============================================================================

fp_t __adddf3(fp_t a, fp_t b) {
    rep_t aRep = toRep(a);
    rep_t bRep = toRep(b);
    const rep_t aAbs = aRep & absMask;
    const rep_t bAbs = bRep & absMask;

    // Detect if a or b is zero, infinity, or NaN
    if (aAbs - REP_C(1) >= infRep - REP_C(1) ||
        bAbs - REP_C(1) >= infRep - REP_C(1)) {
        // NaN + anything = qNaN
        if (aAbs > infRep) return fromRep(toRep(a) | quietBit);
        if (bAbs > infRep) return fromRep(toRep(b) | quietBit);

        if (aAbs == infRep) {
            // +/- infinity + -/+ infinity = qNaN
            if ((toRep(a) ^ toRep(b)) == signBit) return fromRep(qnanRep);
            // infinity + anything else = infinity
            return a;
        }

        // anything + infinity = infinity
        if (bAbs == infRep) return b;

        // zero + anything = anything
        if (!aAbs) {
            // but we need to get the sign right for zero + zero
            if (!bAbs) return fromRep(toRep(a) & toRep(b));
            return b;
        }

        // anything + zero = anything
        if (!bAbs) return a;
    }

    // Swap a and b if necessary so that a has the larger absolute value
    if (bAbs > aAbs) {
        const rep_t temp = aRep;
        aRep = bRep;
        bRep = temp;
    }

    // Extract the exponent and significand from the larger operand
    int aExponent = (aRep >> significandBits) & maxExponent;
    int bExponent = (bRep >> significandBits) & maxExponent;
    rep_t aSignificand = aRep & significandMask;
    rep_t bSignificand = bRep & significandMask;

    // Normalize any denormals, and adjust the exponent accordingly
    if (aExponent == 0) aExponent = normalize(&aSignificand);
    if (bExponent == 0) bExponent = normalize(&bSignificand);

    // The sign of the result is the sign of the larger operand, a.
    const rep_t resultSign = aRep & signBit;
    const bool subtraction = (aRep ^ bRep) & signBit;

    // Shift the significands to give us round, guard and sticky, and or in
    // the implicit bit.
    aSignificand = (aSignificand | implicitBit) << 3;
    bSignificand = (bSignificand | implicitBit) << 3;

    // Shift b significand right to align with a
    const unsigned int align = aExponent - bExponent;
    if (align) {
        if (align < typeWidth) {
            const bool sticky = (bSignificand << (typeWidth - align)) != 0;
            bSignificand = bSignificand >> align | sticky;
        } else {
            bSignificand = 1; // sticky; b is known to be non-zero.
        }
    }

    if (subtraction) {
        aSignificand -= bSignificand;
        // If a == -b, return +zero.
        if (aSignificand == 0) return fromRep(0);

        // If partial cancellation occurred, we need to left-shift the result
        // and adjust the exponent.
        if (aSignificand < implicitBit << 3) {
            const int shift = __builtin_clzll(aSignificand) - (typeWidth - significandBits - 4);
            aSignificand <<= shift;
            aExponent -= shift;
        }
    } else {
        aSignificand += bSignificand;

        // If the addition carried up, we need to right-shift the result and
        // adjust the exponent.
        if (aSignificand & implicitBit << 4) {
            const bool sticky = aSignificand & 1;
            aSignificand = aSignificand >> 1 | sticky;
            aExponent += 1;
        }
    }

    // If we have overflowed the type, return +/- infinity.
    if (aExponent >= maxExponent) return fromRep(infRep | resultSign);

    if (aExponent <= 0) {
        // Result is denormal; denormalize the significand.
        const int shift = 1 - aExponent;
        const bool sticky = (aSignificand << (typeWidth - shift)) != 0;
        aSignificand = aSignificand >> shift | sticky;
        aExponent = 0;
    }

    // Low three bits are round, guard, and sticky.
    const int roundGuardSticky = aSignificand & 7;

    // Shift the significand into place, and mask off the implicit bit.
    rep_t result = aSignificand >> 3 & significandMask;

    // Insert the exponent and sign.
    result |= (rep_t)aExponent << significandBits;
    result |= resultSign;

    // Perform the final rounding.
    if (roundGuardSticky > 4) result++;
    if (roundGuardSticky == 4) result += result & 1;

    return fromRep(result);
}

// ============================================================================
// Double-precision subtraction: __subdf3
// ============================================================================

fp_t __subdf3(fp_t a, fp_t b) {
    return __adddf3(a, fromRep(toRep(b) ^ signBit));
}

// ============================================================================
// Double-precision multiplication: __muldf3
// ============================================================================

fp_t __muldf3(fp_t a, fp_t b) {
    const unsigned int aExponent = (toRep(a) >> significandBits) & maxExponent;
    const unsigned int bExponent = (toRep(b) >> significandBits) & maxExponent;
    const rep_t productSign = (toRep(a) ^ toRep(b)) & signBit;

    rep_t aSignificand = toRep(a) & significandMask;
    rep_t bSignificand = toRep(b) & significandMask;
    int scale = 0;

    // Detect if a or b is zero, denormal, infinity, or NaN.
    if (aExponent - 1U >= maxExponent - 1U ||
        bExponent - 1U >= maxExponent - 1U) {
        const rep_t aAbs = toRep(a) & absMask;
        const rep_t bAbs = toRep(b) & absMask;

        // NaN * anything = qNaN
        if (aAbs > infRep) return fromRep(toRep(a) | quietBit);
        if (bAbs > infRep) return fromRep(toRep(b) | quietBit);

        if (aAbs == infRep) {
            // infinity * zero = NaN
            if (!bAbs) return fromRep(qnanRep);
            // infinity * non-zero = infinity
            return fromRep(aAbs | productSign);
        }

        if (bAbs == infRep) {
            // zero * infinity = NaN
            if (!aAbs) return fromRep(qnanRep);
            // non-zero * infinity = infinity
            return fromRep(bAbs | productSign);
        }

        // zero * anything = +/- zero
        if (!aAbs) return fromRep(productSign);
        // anything * zero = +/- zero
        if (!bAbs) return fromRep(productSign);

        // one or both of a or b is denormal, the other (if applicable) is a
        // normal number.
        if (aExponent == 0) scale += normalize(&aSignificand);
        if (bExponent == 0) scale += normalize(&bSignificand);
    }

    // Or in the implicit bit to get the true significand
    aSignificand |= implicitBit;
    bSignificand |= implicitBit;

    // Get the full product
    const int productExponent = aExponent + bExponent - (maxExponent >> 1) + scale;

    // Multiply 64-bit significands: we just need the high 64-bits
    // For RISC-V 32-bit, we need to do this in parts
    const uint32_t aLo = aSignificand;
    const uint32_t aHi = aSignificand >> 32;
    const uint32_t bLo = bSignificand;
    const uint32_t bHi = bSignificand >> 32;

    const uint64_t plolo = (uint64_t)aLo * bLo;
    const uint64_t plohi = (uint64_t)aLo * bHi;
    const uint64_t philo = (uint64_t)aHi * bLo;
    const uint64_t phihi = (uint64_t)aHi * bHi;

    const uint64_t productLo = plolo + (plohi << 32) + (philo << 32);
    const uint64_t productHi = phihi + (plohi >> 32) + (philo >> 32) +
        ((plolo >> 32) + (uint32_t)plohi + (uint32_t)philo >= (1ULL << 32) ? 1 : 0);

    rep_t productHigh = productHi;
    rep_t productLow = productLo;

    // Normalize the product
    int productExp = productExponent;
    if (productHigh & (implicitBit << 1)) {
        productExp++;
        // Shift right
        const bool sticky = productLow != 0;
        productLow = productHigh << 63 | productLow >> 1 | sticky;
        productHigh >>= 1;
    }

    // If the result is normal, we're done
    if (productExp >= maxExponent) {
        return fromRep(infRep | productSign);
    }

    if (productExp <= 0) {
        // Result is denormal
        const unsigned int shift = 1 - productExp;
        if (shift >= typeWidth) {
            return fromRep(productSign);
        }
        wideRightShiftWithSticky(&productHigh, &productLow, shift);
        productExp = 0;
    }

    // Extract the significand from the product
    rep_t result = productHigh & significandMask;
    result |= (rep_t)productExp << significandBits;
    result |= productSign;

    // Round
    if (productLow > signBit) result++;
    if (productLow == signBit) result += result & 1;

    return fromRep(result);
}

// ============================================================================
// Double-precision division: __divdf3
// ============================================================================

fp_t __divdf3(fp_t a, fp_t b) {
    const unsigned int aExponent = (toRep(a) >> significandBits) & maxExponent;
    const unsigned int bExponent = (toRep(b) >> significandBits) & maxExponent;
    const rep_t quotientSign = (toRep(a) ^ toRep(b)) & signBit;

    rep_t aSignificand = toRep(a) & significandMask;
    rep_t bSignificand = toRep(b) & significandMask;
    int scale = 0;

    // Detect if a or b is zero, denormal, infinity, or NaN.
    if (aExponent - 1U >= maxExponent - 1U ||
        bExponent - 1U >= maxExponent - 1U) {
        const rep_t aAbs = toRep(a) & absMask;
        const rep_t bAbs = toRep(b) & absMask;

        // NaN / anything = qNaN
        if (aAbs > infRep) return fromRep(toRep(a) | quietBit);
        // anything / NaN = qNaN
        if (bAbs > infRep) return fromRep(toRep(b) | quietBit);

        if (aAbs == infRep) {
            // infinity / infinity = NaN
            if (bAbs == infRep) return fromRep(qnanRep);
            // infinity / finite = infinity
            return fromRep(aAbs | quotientSign);
        }

        // finite / infinity = zero
        if (bAbs == infRep) return fromRep(quotientSign);

        if (!aAbs) {
            // zero / zero = NaN
            if (!bAbs) return fromRep(qnanRep);
            // zero / finite = zero
            return fromRep(quotientSign);
        }
        // finite / zero = infinity
        if (!bAbs) return fromRep(infRep | quotientSign);

        // denormal handling
        if (aExponent == 0) scale += normalize(&aSignificand);
        if (bExponent == 0) scale -= normalize(&bSignificand);
    }

    // Or in the implicit bit
    aSignificand |= implicitBit;
    bSignificand |= implicitBit;

    int writtenExponent = (aExponent - bExponent + (maxExponent >> 1)) + scale + 1;

    // Align the significand of a in the high bits
    rep_t quotient = 0;
    // rep_t quotientLo = 0; // FIXME: unused

    // Long division
    aSignificand <<= 11;  // leave room for the quotient
    bSignificand <<= 11;

    // Main division loop
    for (int i = 0; i < significandBits + 2; i++) {
        quotient <<= 1;
        if (aSignificand >= bSignificand) {
            aSignificand -= bSignificand;
            quotient |= 1;
        }
        aSignificand <<= 1;
    }

    // Sticky bit
    quotient |= (aSignificand != 0);

    // Normalize
    if (!(quotient & (implicitBit << 1))) {
        quotient <<= 1;
        writtenExponent--;
    }

    // Handle exponent overflow/underflow
    if (writtenExponent >= maxExponent) {
        return fromRep(infRep | quotientSign);
    }

    if (writtenExponent <= 0) {
        if (writtenExponent < -(int)significandBits) {
            return fromRep(quotientSign);
        }
        const unsigned int shift = 1 - writtenExponent;
        const bool sticky = (quotient << (typeWidth - shift)) != 0;
        quotient = quotient >> shift | sticky;
        writtenExponent = 0;
    }

    // Round
    const int roundBits = quotient & 7;
    quotient >>= 3;

    rep_t result = quotient & significandMask;
    result |= (rep_t)writtenExponent << significandBits;
    result |= quotientSign;

    if (roundBits > 4) result++;
    if (roundBits == 4) result += result & 1;

    return fromRep(result);
}

// ============================================================================
// Integer to double conversion
// ============================================================================

// __floatsidf: signed int -> double
fp_t __floatsidf(si_int a) {
    if (a == 0) return fromRep(0);

    const rep_t sign = a < 0 ? signBit : 0;
    su_int absA = a < 0 ? -(su_int)a : (su_int)a;

    // Calculate exponent
    const int exponent = (maxExponent >> 1) + 31 - __builtin_clz(absA);

    // Shift to get significand
    rep_t result;
    const int shift = significandBits - (31 - __builtin_clz(absA));
    if (shift >= 0) {
        result = (rep_t)absA << shift;
    } else {
        // Need to round
        const su_int sticky = absA << (32 + shift);
        absA >>= -shift;
        result = absA;
        // Round to nearest, ties to even
        result += (sticky > signBit) || ((sticky == signBit) && (result & 1));
    }

    result &= significandMask;
    result |= (rep_t)exponent << significandBits;
    result |= sign;

    return fromRep(result);
}

// __floatdidf: signed long long -> double
fp_t __floatdidf(di_int a) {
    if (a == 0) return fromRep(0);

    const rep_t sign = a < 0 ? signBit : 0;
    du_int absA = a < 0 ? -(du_int)a : (du_int)a;

    // Calculate exponent
    const int exponent = (maxExponent >> 1) + 63 - __builtin_clzll(absA);

    // Shift to get significand
    rep_t result;
    const int shift = significandBits - (63 - __builtin_clzll(absA));
    if (shift >= 0) {
        result = absA << shift;
    } else {
        // Need to round
        const du_int sticky = absA << (64 + shift);
        absA >>= -shift;
        result = absA;
        // Round to nearest, ties to even
        result += (sticky > signBit) || ((sticky == signBit) && (result & 1));
    }

    result &= significandMask;
    result |= (rep_t)exponent << significandBits;
    result |= sign;

    return fromRep(result);
}

// __floatunsidf: unsigned int -> double
fp_t __floatunsidf(su_int a) {
    if (a == 0) return fromRep(0);

    // Calculate exponent
    const int exponent = (maxExponent >> 1) + 31 - __builtin_clz(a);

    // Shift to get significand
    rep_t result;
    const int shift = significandBits - (31 - __builtin_clz(a));
    result = (rep_t)a << shift;

    result &= significandMask;
    result |= (rep_t)exponent << significandBits;

    return fromRep(result);
}

// __floatundidf: unsigned long long -> double
fp_t __floatundidf(du_int a) {
    if (a == 0) return fromRep(0);

    // Calculate exponent
    const int exponent = (maxExponent >> 1) + 63 - __builtin_clzll(a);

    // Shift to get significand
    rep_t result;
    const int shift = significandBits - (63 - __builtin_clzll(a));
    if (shift >= 0) {
        result = a << shift;
    } else {
        // Need to round
        const du_int sticky = a << (64 + shift);
        a >>= -shift;
        result = a;
        // Round to nearest, ties to even
        result += (sticky > signBit) || ((sticky == signBit) && (result & 1));
    }

    result &= significandMask;
    result |= (rep_t)exponent << significandBits;

    return fromRep(result);
}

// ============================================================================
// Double to integer conversion
// ============================================================================

// __fixdfsi: double -> signed int
si_int __fixdfsi(fp_t a) {
    const rep_t aRep = toRep(a);
    const rep_t aAbs = aRep & absMask;
    const si_int sign = aRep & signBit ? -1 : 1;
    const int exponent = (aAbs >> significandBits) - (maxExponent >> 1);

    // If exponent is negative, the result is zero
    if (exponent < 0) return 0;

    // If the value is out of range, clamp
    if (exponent >= 31) {
        return sign == 1 ? INT32_MAX : INT32_MIN;
    }

    // Extract the significand
    rep_t significand = (aAbs & significandMask) | implicitBit;

    // Shift to get the integer part
    if (exponent < significandBits) {
        significand >>= significandBits - exponent;
    } else {
        significand <<= exponent - significandBits;
    }

    return sign * (si_int)significand;
}

// __fixdfdi: double -> signed long long
di_int __fixdfdi(fp_t a) {
    const rep_t aRep = toRep(a);
    const rep_t aAbs = aRep & absMask;
    const di_int sign = aRep & signBit ? -1 : 1;
    const int exponent = (aAbs >> significandBits) - (maxExponent >> 1);

    // If exponent is negative, the result is zero
    if (exponent < 0) return 0;

    // If the value is out of range, clamp
    if (exponent >= 63) {
        return sign == 1 ? INT64_MAX : INT64_MIN;
    }

    // Extract the significand
    rep_t significand = (aAbs & significandMask) | implicitBit;

    // Shift to get the integer part
    if (exponent < significandBits) {
        significand >>= significandBits - exponent;
    } else {
        significand <<= exponent - significandBits;
    }

    return sign * (di_int)significand;
}

// __fixunsdfsi: double -> unsigned int
su_int __fixunsdfsi(fp_t a) {
    const rep_t aRep = toRep(a);
    const rep_t aAbs = aRep & absMask;
    const int sign = aRep & signBit ? -1 : 1;
    const int exponent = (aAbs >> significandBits) - (maxExponent >> 1);

    // If negative or exponent is negative, the result is zero
    if (sign < 0 || exponent < 0) return 0;

    // If the value is out of range, clamp
    if (exponent >= 32) {
        return UINT32_MAX;
    }

    // Extract the significand
    rep_t significand = (aAbs & significandMask) | implicitBit;

    // Shift to get the integer part
    if (exponent < significandBits) {
        significand >>= significandBits - exponent;
    } else {
        significand <<= exponent - significandBits;
    }

    return (su_int)significand;
}

// __fixunsdfdi: double -> unsigned long long
du_int __fixunsdfdi(fp_t a) {
    const rep_t aRep = toRep(a);
    const rep_t aAbs = aRep & absMask;
    const int sign = aRep & signBit ? -1 : 1;
    const int exponent = (aAbs >> significandBits) - (maxExponent >> 1);

    // If negative or exponent is negative, the result is zero
    if (sign < 0 || exponent < 0) return 0;

    // If the value is out of range, clamp
    if (exponent >= 64) {
        return UINT64_MAX;
    }

    // Extract the significand
    rep_t significand = (aAbs & significandMask) | implicitBit;

    // Shift to get the integer part
    if (exponent < significandBits) {
        significand >>= significandBits - exponent;
    } else {
        significand <<= exponent - significandBits;
    }

    return significand;
}

// ============================================================================
// Single-precision (float) operations
// ============================================================================

typedef float fp_s;
typedef uint32_t srep_t_s;

#define SREP_C UINT32_C
#define significandBitsS 23

static const int typeWidthS = sizeof(srep_t_s) * CHAR_BIT;
static const int exponentBitsS = typeWidthS - significandBitsS - 1;
static const int maxExponentS = (1 << exponentBitsS) - 1;
static const srep_t_s implicitBitS = SREP_C(1) << significandBitsS;
static const srep_t_s significandMaskS = implicitBitS - 1;
static const srep_t_s signBitS = SREP_C(1) << (significandBitsS + exponentBitsS);
static const srep_t_s absMaskS = signBitS - 1;
static const srep_t_s infRepS = (srep_t_s)maxExponentS << significandBitsS;
static const srep_t_s quietBitS = implicitBitS >> 1;
static const srep_t_s qnanRepS = infRepS | quietBitS;

static inline srep_t_s toRepS(fp_s x) {
    union { fp_s f; srep_t_s i; } u;
    u.f = x;
    return u.i;
}

static inline fp_s fromRepS(srep_t_s x) {
    union { fp_s f; srep_t_s i; } u;
    u.i = x;
    return u.f;
}

static inline int normalizeS(srep_t_s *significand) {
    const int shift = __builtin_clz(*significand) - (typeWidthS - significandBitsS - 1);
    *significand <<= shift;
    return 1 - shift;
}

// ============================================================================
// Single-precision addition: __addsf3
// ============================================================================

fp_s __addsf3(fp_s a, fp_s b) {
    srep_t_s aRep = toRepS(a);
    srep_t_s bRep = toRepS(b);
    const srep_t_s aAbs = aRep & absMaskS;
    const srep_t_s bAbs = bRep & absMaskS;

    if (aAbs - SREP_C(1) >= infRepS - SREP_C(1) ||
        bAbs - SREP_C(1) >= infRepS - SREP_C(1)) {
        if (aAbs > infRepS) return fromRepS(toRepS(a) | quietBitS);
        if (bAbs > infRepS) return fromRepS(toRepS(b) | quietBitS);

        if (aAbs == infRepS) {
            if ((toRepS(a) ^ toRepS(b)) == signBitS) return fromRepS(qnanRepS);
            return a;
        }
        if (bAbs == infRepS) return b;
        if (!aAbs) {
            if (!bAbs) return fromRepS(toRepS(a) & toRepS(b));
            return b;
        }
        if (!bAbs) return a;
    }

    if (bAbs > aAbs) {
        const srep_t_s temp = aRep;
        aRep = bRep;
        bRep = temp;
    }

    int aExponent = (aRep >> significandBitsS) & maxExponentS;
    int bExponent = (bRep >> significandBitsS) & maxExponentS;
    srep_t_s aSignificand = aRep & significandMaskS;
    srep_t_s bSignificand = bRep & significandMaskS;

    if (aExponent == 0) aExponent = normalizeS(&aSignificand);
    if (bExponent == 0) bExponent = normalizeS(&bSignificand);

    const srep_t_s resultSign = aRep & signBitS;
    const bool subtraction = (aRep ^ bRep) & signBitS;

    aSignificand = (aSignificand | implicitBitS) << 3;
    bSignificand = (bSignificand | implicitBitS) << 3;

    const unsigned int align = aExponent - bExponent;
    if (align) {
        if (align < typeWidthS) {
            const bool sticky = (bSignificand << (typeWidthS - align)) != 0;
            bSignificand = bSignificand >> align | sticky;
        } else {
            bSignificand = 1;
        }
    }

    if (subtraction) {
        aSignificand -= bSignificand;
        if (aSignificand == 0) return fromRepS(0);
        if (aSignificand < implicitBitS << 3) {
            const int shift = __builtin_clz(aSignificand) - (typeWidthS - significandBitsS - 4);
            aSignificand <<= shift;
            aExponent -= shift;
        }
    } else {
        aSignificand += bSignificand;
        if (aSignificand & implicitBitS << 4) {
            const bool sticky = aSignificand & 1;
            aSignificand = aSignificand >> 1 | sticky;
            aExponent += 1;
        }
    }

    if (aExponent >= maxExponentS) return fromRepS(infRepS | resultSign);

    if (aExponent <= 0) {
        const int shift = 1 - aExponent;
        const bool sticky = (aSignificand << (typeWidthS - shift)) != 0;
        aSignificand = aSignificand >> shift | sticky;
        aExponent = 0;
    }

    const int roundGuardSticky = aSignificand & 7;
    srep_t_s result = aSignificand >> 3 & significandMaskS;
    result |= (srep_t_s)aExponent << significandBitsS;
    result |= resultSign;

    if (roundGuardSticky > 4) result++;
    if (roundGuardSticky == 4) result += result & 1;

    return fromRepS(result);
}

// __subsf3: single subtraction
fp_s __subsf3(fp_s a, fp_s b) {
    return __addsf3(a, fromRepS(toRepS(b) ^ signBitS));
}

// ============================================================================
// Single-precision multiplication: __mulsf3
// ============================================================================

fp_s __mulsf3(fp_s a, fp_s b) {
    const unsigned int aExponent = (toRepS(a) >> significandBitsS) & maxExponentS;
    const unsigned int bExponent = (toRepS(b) >> significandBitsS) & maxExponentS;
    const srep_t_s productSign = (toRepS(a) ^ toRepS(b)) & signBitS;

    srep_t_s aSignificand = toRepS(a) & significandMaskS;
    srep_t_s bSignificand = toRepS(b) & significandMaskS;
    int scale = 0;

    if (aExponent - 1U >= maxExponentS - 1U ||
        bExponent - 1U >= maxExponentS - 1U) {
        const srep_t_s aAbs = toRepS(a) & absMaskS;
        const srep_t_s bAbs = toRepS(b) & absMaskS;

        if (aAbs > infRepS) return fromRepS(toRepS(a) | quietBitS);
        if (bAbs > infRepS) return fromRepS(toRepS(b) | quietBitS);

        if (aAbs == infRepS) {
            if (!bAbs) return fromRepS(qnanRepS);
            return fromRepS(aAbs | productSign);
        }
        if (bAbs == infRepS) {
            if (!aAbs) return fromRepS(qnanRepS);
            return fromRepS(bAbs | productSign);
        }
        if (!aAbs) return fromRepS(productSign);
        if (!bAbs) return fromRepS(productSign);

        if (aExponent == 0) scale += normalizeS(&aSignificand);
        if (bExponent == 0) scale += normalizeS(&bSignificand);
    }

    aSignificand |= implicitBitS;
    bSignificand |= implicitBitS;

    const int productExponent = aExponent + bExponent - (maxExponentS >> 1) + scale;

    const uint64_t product = (uint64_t)aSignificand * bSignificand;
    srep_t_s productHigh = product >> 32;
    srep_t_s productLow = product;

    int productExp = productExponent;
    if (product & (1ULL << (significandBitsS * 2 + 1))) {
        productExp++;
        productHigh = product >> (significandBitsS + 2);
        productLow = (product >> 1) | (product & 1);
    } else {
        productHigh = product >> (significandBitsS + 1);
        productLow = product << (32 - significandBitsS - 1);
    }

    if (productExp >= maxExponentS) {
        return fromRepS(infRepS | productSign);
    }

    if (productExp <= 0) {
        const unsigned int shift = 1 - productExp;
        if (shift >= typeWidthS) {
            return fromRepS(productSign);
        }
        productHigh >>= shift;
        productExp = 0;
    }

    srep_t_s result = productHigh & significandMaskS;
    result |= (srep_t_s)productExp << significandBitsS;
    result |= productSign;

    // Rounding
    if (productLow > (1U << 31)) result++;
    if (productLow == (1U << 31)) result += result & 1;

    return fromRepS(result);
}

// ============================================================================
// Single-precision division: __divsf3
// ============================================================================

fp_s __divsf3(fp_s a, fp_s b) {
    const unsigned int aExponent = (toRepS(a) >> significandBitsS) & maxExponentS;
    const unsigned int bExponent = (toRepS(b) >> significandBitsS) & maxExponentS;
    const srep_t_s quotientSign = (toRepS(a) ^ toRepS(b)) & signBitS;

    srep_t_s aSignificand = toRepS(a) & significandMaskS;
    srep_t_s bSignificand = toRepS(b) & significandMaskS;
    int scale = 0;

    if (aExponent - 1U >= maxExponentS - 1U ||
        bExponent - 1U >= maxExponentS - 1U) {
        const srep_t_s aAbs = toRepS(a) & absMaskS;
        const srep_t_s bAbs = toRepS(b) & absMaskS;

        if (aAbs > infRepS) return fromRepS(toRepS(a) | quietBitS);
        if (bAbs > infRepS) return fromRepS(toRepS(b) | quietBitS);

        if (aAbs == infRepS) {
            if (bAbs == infRepS) return fromRepS(qnanRepS);
            return fromRepS(aAbs | quotientSign);
        }
        if (bAbs == infRepS) return fromRepS(quotientSign);

        if (!aAbs) {
            if (!bAbs) return fromRepS(qnanRepS);
            return fromRepS(quotientSign);
        }
        if (!bAbs) return fromRepS(infRepS | quotientSign);

        if (aExponent == 0) scale += normalizeS(&aSignificand);
        if (bExponent == 0) scale -= normalizeS(&bSignificand);
    }

    aSignificand |= implicitBitS;
    bSignificand |= implicitBitS;

    int writtenExponent = (aExponent - bExponent + (maxExponentS >> 1)) + scale + 1;

    // Use 64-bit arithmetic for the division
    uint64_t aWide = (uint64_t)aSignificand << 31;
    srep_t_s quotient = aWide / bSignificand;
    srep_t_s remainder = aWide % bSignificand;

    // Normalize
    if (!(quotient & (implicitBitS << 8))) {
        quotient <<= 1;
        quotient |= (remainder >= bSignificand / 2);
        writtenExponent--;
    }

    // Sticky bit
    quotient |= (remainder != 0);

    if (writtenExponent >= maxExponentS) {
        return fromRepS(infRepS | quotientSign);
    }

    if (writtenExponent <= 0) {
        if (writtenExponent < -(int)significandBitsS) {
            return fromRepS(quotientSign);
        }
        const unsigned int shift = 1 - writtenExponent;
        const bool sticky = (quotient << (typeWidthS - shift)) != 0;
        quotient = quotient >> shift | sticky;
        writtenExponent = 0;
    }

    const int roundBits = quotient & 0xFF;
    quotient >>= 8;

    srep_t_s result = quotient & significandMaskS;
    result |= (srep_t_s)writtenExponent << significandBitsS;
    result |= quotientSign;

    if (roundBits > 128) result++;
    if (roundBits == 128) result += result & 1;

    return fromRepS(result);
}

// ============================================================================
// Single-precision conversion functions
// ============================================================================

// __floatsisf: signed int -> float
fp_s __floatsisf(si_int a) {
    if (a == 0) return fromRepS(0);

    const srep_t_s sign = a < 0 ? signBitS : 0;
    su_int absA = a < 0 ? -(su_int)a : (su_int)a;

    const int exponent = (maxExponentS >> 1) + 31 - __builtin_clz(absA);

    srep_t_s result;
    const int shift = significandBitsS - (31 - __builtin_clz(absA));
    if (shift >= 0) {
        result = absA << shift;
    } else {
        const su_int sticky = absA << (32 + shift);
        absA >>= -shift;
        result = absA;
        result += (sticky > signBitS) || ((sticky == signBitS) && (result & 1));
    }

    result &= significandMaskS;
    result |= (srep_t_s)exponent << significandBitsS;
    result |= sign;

    return fromRepS(result);
}

// __floatunsisf: unsigned int -> float
fp_s __floatunsisf(su_int a) {
    if (a == 0) return fromRepS(0);

    const int exponent = (maxExponentS >> 1) + 31 - __builtin_clz(a);

    srep_t_s result;
    const int shift = significandBitsS - (31 - __builtin_clz(a));
    if (shift >= 0) {
        result = a << shift;
    } else {
        const su_int sticky = a << (32 + shift);
        a >>= -shift;
        result = a;
        result += (sticky > signBitS) || ((sticky == signBitS) && (result & 1));
    }

    result &= significandMaskS;
    result |= (srep_t_s)exponent << significandBitsS;

    return fromRepS(result);
}

// __fixsfsi: float -> signed int
si_int __fixsfsi(fp_s a) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s aAbs = aRep & absMaskS;
    const si_int sign = aRep & signBitS ? -1 : 1;
    const int exponent = (aAbs >> significandBitsS) - (maxExponentS >> 1);

    if (exponent < 0) return 0;
    if (exponent >= 31) {
        return sign == 1 ? INT32_MAX : INT32_MIN;
    }

    srep_t_s significand = (aAbs & significandMaskS) | implicitBitS;

    if (exponent < significandBitsS) {
        significand >>= significandBitsS - exponent;
    } else {
        significand <<= exponent - significandBitsS;
    }

    return sign * (si_int)significand;
}

// __fixunssfsi: float -> unsigned int
su_int __fixunssfsi(fp_s a) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s aAbs = aRep & absMaskS;
    const int sign = aRep & signBitS ? -1 : 1;
    const int exponent = (aAbs >> significandBitsS) - (maxExponentS >> 1);

    if (sign < 0 || exponent < 0) return 0;
    if (exponent >= 32) return UINT32_MAX;

    srep_t_s significand = (aAbs & significandMaskS) | implicitBitS;

    if (exponent < significandBitsS) {
        significand >>= significandBitsS - exponent;
    } else {
        significand <<= exponent - significandBitsS;
    }

    return significand;
}

// ============================================================================
// Float <-> Double conversion
// ============================================================================

// __extendsfdf2: float -> double
fp_t __extendsfdf2(fp_s a) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s aAbs = aRep & absMaskS;

    // Handle special cases
    if (aAbs > infRepS) {
        // NaN - quiet it and extend
        return fromRep((rep_t)(aRep & signBitS) << 32 | qnanRep);
    }
    if (aAbs == infRepS) {
        // Infinity
        return fromRep((rep_t)(aRep & signBitS) << 32 | infRep);
    }
    if (aAbs == 0) {
        // Zero
        return fromRep((rep_t)(aRep & signBitS) << 32);
    }

    const rep_t sign = (rep_t)(aRep & signBitS) << 32;
    int exponent = (aAbs >> significandBitsS);
    srep_t_s significand = aAbs & significandMaskS;

    // Handle denormals
    if (exponent == 0) {
        exponent = 1;
        while (!(significand & implicitBitS)) {
            significand <<= 1;
            exponent--;
        }
        significand &= significandMaskS;
    }

    // Adjust exponent bias: float bias = 127, double bias = 1023
    exponent = exponent - 127 + 1023;

    rep_t result = sign;
    result |= (rep_t)exponent << significandBits;
    result |= (rep_t)significand << (significandBits - significandBitsS);

    return fromRep(result);
}

// __truncdfsf2: double -> float
fp_s __truncdfsf2(fp_t a) {
    const rep_t aRep = toRep(a);
    const rep_t aAbs = aRep & absMask;
    const srep_t_s sign = (aRep >> 32) & signBitS;

    // Handle special cases
    if (aAbs > infRep) {
        // NaN
        return fromRepS(sign | qnanRepS);
    }
    if (aAbs >= infRep) {
        // Infinity or overflow
        return fromRepS(sign | infRepS);
    }
    if (aAbs == 0) {
        // Zero
        return fromRepS(sign);
    }

    int exponent = (aAbs >> significandBits) - 1023 + 127;
    rep_t significand = aAbs & significandMask;

    // Check for overflow to infinity
    if (exponent >= maxExponentS) {
        return fromRepS(sign | infRepS);
    }

    // Check for underflow to zero or denormal
    if (exponent <= 0) {
        if (exponent < -significandBitsS) {
            return fromRepS(sign);
        }
        // Denormal result
        significand |= implicitBit;
        const int shift = 1 - exponent + (significandBits - significandBitsS);
        const rep_t sticky = (significand << (64 - shift)) != 0;
        significand = (significand >> shift) | sticky;
        exponent = 0;

        // Round
        const int round = (significand >> (significandBits - significandBitsS - 1)) & 7;
        significand >>= significandBits - significandBitsS;
        if (round > 4) significand++;
        if (round == 4) significand += significand & 1;

        return fromRepS(sign | (srep_t_s)significand);
    }

    // Normal result - shift and round
    const int shift = significandBits - significandBitsS;
    const rep_t roundBit = REP_C(1) << (shift - 1);
    const rep_t sticky = (significand & (roundBit - 1)) != 0;

    srep_t_s result = sign;
    result |= (srep_t_s)exponent << significandBitsS;
    result |= (srep_t_s)(significand >> shift) & significandMaskS;

    // Round to nearest, ties to even
    if ((significand & roundBit) && (sticky || (result & 1))) {
        result++;
    }

    return fromRepS(result);
}

// ============================================================================
// Comparison functions
// ============================================================================

// __eqdf2, __nedf2: returns 0 if equal
int __eqdf2(fp_t a, fp_t b) {
    const rep_t aRep = toRep(a);
    const rep_t bRep = toRep(b);
    const rep_t aAbs = aRep & absMask;
    const rep_t bAbs = bRep & absMask;

    // NaN comparison always returns unequal
    if (aAbs > infRep || bAbs > infRep) return 1;

    // +0 == -0
    if ((aAbs | bAbs) == 0) return 0;

    return aRep != bRep;
}

int __nedf2(fp_t a, fp_t b) {
    return __eqdf2(a, b);
}

// __ltdf2, __ledf2: returns -1 if a < b, 0 if a == b, 1 if a > b or unordered
int __ltdf2(fp_t a, fp_t b) {
    const rep_t aRep = toRep(a);
    const rep_t bRep = toRep(b);
    const rep_t aAbs = aRep & absMask;
    const rep_t bAbs = bRep & absMask;

    // NaN
    if (aAbs > infRep || bAbs > infRep) return 1;

    // +0 == -0
    if ((aAbs | bAbs) == 0) return 0;

    const int aSign = aRep >> 63;
    const int bSign = bRep >> 63;

    if (aSign != bSign) {
        return aSign ? -1 : 1;
    }

    if (aSign) {
        return aRep > bRep ? -1 : (aRep < bRep ? 1 : 0);
    } else {
        return aRep < bRep ? -1 : (aRep > bRep ? 1 : 0);
    }
}

int __ledf2(fp_t a, fp_t b) {
    return __ltdf2(a, b);
}

// __gtdf2, __gedf2: returns 1 if a > b, 0 if a == b, -1 if a < b or unordered
int __gtdf2(fp_t a, fp_t b) {
    const rep_t aRep = toRep(a);
    const rep_t bRep = toRep(b);
    const rep_t aAbs = aRep & absMask;
    const rep_t bAbs = bRep & absMask;

    // NaN
    if (aAbs > infRep || bAbs > infRep) return -1;

    // +0 == -0
    if ((aAbs | bAbs) == 0) return 0;

    const int aSign = aRep >> 63;
    const int bSign = bRep >> 63;

    if (aSign != bSign) {
        return aSign ? -1 : 1;
    }

    if (aSign) {
        return aRep > bRep ? -1 : (aRep < bRep ? 1 : 0);
    } else {
        return aRep < bRep ? -1 : (aRep > bRep ? 1 : 0);
    }
}

int __gedf2(fp_t a, fp_t b) {
    return __gtdf2(a, b);
}

// __unorddf2: returns nonzero if either operand is NaN
int __unorddf2(fp_t a, fp_t b) {
    const rep_t aAbs = toRep(a) & absMask;
    const rep_t bAbs = toRep(b) & absMask;
    return aAbs > infRep || bAbs > infRep;
}

// ============================================================================
// Single-precision comparison functions
// ============================================================================

int __eqsf2(fp_s a, fp_s b) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s bRep = toRepS(b);
    const srep_t_s aAbs = aRep & absMaskS;
    const srep_t_s bAbs = bRep & absMaskS;

    if (aAbs > infRepS || bAbs > infRepS) return 1;
    if ((aAbs | bAbs) == 0) return 0;
    return aRep != bRep;
}

int __nesf2(fp_s a, fp_s b) {
    return __eqsf2(a, b);
}

int __ltsf2(fp_s a, fp_s b) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s bRep = toRepS(b);
    const srep_t_s aAbs = aRep & absMaskS;
    const srep_t_s bAbs = bRep & absMaskS;

    if (aAbs > infRepS || bAbs > infRepS) return 1;
    if ((aAbs | bAbs) == 0) return 0;

    const int aSign = aRep >> 31;
    const int bSign = bRep >> 31;

    if (aSign != bSign) {
        return aSign ? -1 : 1;
    }

    if (aSign) {
        return aRep > bRep ? -1 : (aRep < bRep ? 1 : 0);
    } else {
        return aRep < bRep ? -1 : (aRep > bRep ? 1 : 0);
    }
}

int __lesf2(fp_s a, fp_s b) {
    return __ltsf2(a, b);
}

int __gtsf2(fp_s a, fp_s b) {
    const srep_t_s aRep = toRepS(a);
    const srep_t_s bRep = toRepS(b);
    const srep_t_s aAbs = aRep & absMaskS;
    const srep_t_s bAbs = bRep & absMaskS;

    if (aAbs > infRepS || bAbs > infRepS) return -1;
    if ((aAbs | bAbs) == 0) return 0;

    const int aSign = aRep >> 31;
    const int bSign = bRep >> 31;

    if (aSign != bSign) {
        return aSign ? -1 : 1;
    }

    if (aSign) {
        return aRep > bRep ? -1 : (aRep < bRep ? 1 : 0);
    } else {
        return aRep < bRep ? -1 : (aRep > bRep ? 1 : 0);
    }
}

int __gesf2(fp_s a, fp_s b) {
    return __gtsf2(a, b);
}

int __unordsf2(fp_s a, fp_s b) {
    const srep_t_s aAbs = toRepS(a) & absMaskS;
    const srep_t_s bAbs = toRepS(b) & absMaskS;
    return aAbs > infRepS || bAbs > infRepS;
}

// ============================================================================
// Negation
// ============================================================================

fp_t __negdf2(fp_t a) {
    return fromRep(toRep(a) ^ signBit);
}

fp_s __negsf2(fp_s a) {
    return fromRepS(toRepS(a) ^ signBitS);
}
