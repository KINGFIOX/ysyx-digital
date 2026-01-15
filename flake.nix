{
  description = "YSYX (一生一芯) 开发环境";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };

        # 创建空的 stubs-ilp32.h 来修复 rv32 编译问题
        stubsIlp32Fix = pkgs.runCommand "stubs-ilp32-fix" { } ''
          mkdir -p $out/include/gnu
          echo "/* Empty stub for rv32 ilp32 ABI compatibility */" > $out/include/gnu/stubs-ilp32.h
        '';
      in
      {
        devShells.default = pkgs.mkShell {
          name = "ysyx-dev";

          packages = with pkgs; [
            # ========================
            # 基础构建工具
            # ========================
            gnumake
            cmake
            ninja
            pkg-config
            autoconf
            automake

            # ========================
            # C/C++ 工具链
            # ========================
            gcc
            gdb
            lldb
            clang-tools # clangd, clang-format 等
            bear

            # ========================
            # NEMU 依赖
            # ========================
            flex
            bison
            readline
            ncurses
            libllvm
            dtc # spike

            # ========================
            # NPC (Chisel/Scala) 依赖
            # ========================
            jdk21
            scala_2_13
            circt # 包含 firtool，Chisel 生成 Verilog 需要
            metals # mill 不会自动下载
            mill # 1.0.6

            # ========================
            # Verilog/仿真工具
            # ========================
            verilator
            gtkwave # 波形查看器 (可选)

            # ========================
            # NVBoard / 图形界面依赖
            # ========================
            SDL2
            SDL2_image
            SDL2_ttf
            SDL2_mixer # 可能需要音频支持

            # ========================
            # RISC-V 交叉编译工具链
            # ========================
            pkgsCross.riscv32.buildPackages.gcc
            stubsIlp32Fix # 修复缺少的 stubs-ilp32.h

            # ========================
            # 实用工具
            # ========================
            git
            python3
            bear # 生成 compile_commands.json
            ccache
          ];

          # 环境变量设置
          STUBS_ILP32_FIX = "${stubsIlp32Fix}/include";

          shellHook = ''
            # 设置项目根目录
            export YSYX_HOME="$(pwd)"
            export NEMU_HOME="$YSYX_HOME/nemu"
            export AM_HOME="$YSYX_HOME/abstract-machine"
            export NPC_HOME="$YSYX_HOME/npc"
            export NVBOARD_HOME="$YSYX_HOME/nvboard"

            # 重要：覆盖 Nix 设置的交叉编译器，使用原生编译器作为默认
            export CC="${pkgs.gcc}/bin/gcc"
            export CXX="${pkgs.gcc}/bin/g++"

            # RISC-V 工具链前缀
            export CROSS_COMPILE=riscv32-unknown-linux-gnu-

            # Java 设置 (for Mill/Scala)
            export JAVA_HOME="${pkgs.jdk21}"

            # Chisel/CIRCT: 使用系统的 firtool
            export CHISEL_FIRTOOL_PATH="${pkgs.circt}/bin"

            # SDL2 配置
            export SDL2_CONFIG="${pkgs.SDL2}/bin/sdl2-config"

            # yosys-sta 路径
            export YOSYS_STA_HOME="$YSYX_HOME/yosys-sta"

            # GDB 自动加载安全路径配置
            # 确保 GDB 可以自动加载项目中的 .gdbinit 文件
            mkdir -p "$HOME/.config/gdb"
            if ! grep -q "add-auto-load-safe-path.*$NPC_HOME" "$HOME/.config/gdb/gdbinit" 2>/dev/null; then
              echo "add-auto-load-safe-path $NPC_HOME" >> "$HOME/.config/gdb/gdbinit"
            fi

            echo "🚀 YSYX 开发环境已加载!"
            echo "   NEMU_HOME:    $NEMU_HOME"
            echo "   AM_HOME:      $AM_HOME"
            echo "   NPC_HOME:     $NPC_HOME"
            echo "   NVBOARD_HOME: $NVBOARD_HOME"
            echo "   YOSYS_STA_HOME: $YOSYS_STA_HOME"
            echo ""
            echo "📦 可用工具: gcc, verilator, gdb..."
            echo "🔧 RISC-V 工具链: $CROSS_COMPILE"
          '';

          # 确保 C/C++ 编译器能找到头文件和库
          hardeningDisable = [ "all" ];

          # NIX_CFLAGS_COMPILE 和 NIX_LDFLAGS 会自动设置
        };
      }
    );
}
