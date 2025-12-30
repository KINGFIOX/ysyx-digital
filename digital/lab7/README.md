# make sta 的结果 ./result

- abc.sdc：综合/STA 约束文件（时钟、IO 时序等）。
- sta.log：静态时序分析运行日志。
- synth_check.txt：综合后的基础一致性检查结果（连线、端口等）。
- synth_stat.txt：综合统计（单元数量、面积、层次等）。
- top.cap：节点/网络电容报告。
- top.fanout：网络扇出统计。
- top_hold.skew：保持时序偏斜/余量报告。
- top_setup.skew：建立时序偏斜/余量报告。
- top.trans：过渡时间（slew）/转换率报告。
- top_instance.csv：实例级信息表（单元、位置/功耗/面积等，供后续分析）。
- top_instance.pwr：实例功耗明细。
- top.pwr：整体功耗汇总。
- top.netlist.v：综合后的门级网表。
- top.netlist.v.sim：为仿真准备的网表版本（可能含仿真宏/去除受限单元）。
- top.v：一般是综合输入或中间版本的顶层 RTL/网表。
- top.rpt：综合/时序综合的总览报告（面积、时序、违例摘要）。
- yosys.log：Yosys 综合完整日志。