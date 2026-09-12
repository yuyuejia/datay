package com.data.datafusion.service.etl;

/**
 * ETL 节点翻译器 SPI。
 * <p>用于将「设计器组件」翻译为后端 DataY Core 可直接执行的任务单元定义。
 * <p>典型场景：设计器提供了更适合业务配置的组件（如「模型写入」只选择数据模型），
 * 而执行引擎只认底层组件（如 {@code StreamJdbcOutput}）。此时通过实现该接口，
 * 把业务配置展开为引擎所需的参数，而无需改动 {@code ETLTaskService} 的主流程。
 * <p>实现类需注册为 Spring Bean，新增组件翻译只需新增一个实现类即可，符合开闭原则。
 */
public interface ETLNodeTranslator {

    /**
     * 需要翻译的设计器组件类型（对应 {@code dp_etl_component.code}）。
     *
     * @return 组件类型编码
     */
    String supportedType();

    /**
     * 执行翻译：将设计器组件的配置转换为引擎可执行的任务单元定义。
     * <p>实现应自行向 {@code context.getUnit()} 填充所需的全部参数（含 {@code .name}）。
     *
     * @param context 翻译上下文
     */
    void translate(ETLNodeTranslationContext context);
}
