/**
 * 数据服务应用市场（数据应用资产包）模块。
 *
 * <p>本模块把「数据模型、指标、ETL 任务、SQL 任务、数据源、编排任务」打包为一份可移植的
 * JSON 资产包，并支持在任意租户中初始化，初始化时统一完成 ID 重映射（含数据源 ID）。
 *
 * <p>模块内既有 {@code service.apppackage.model} 的清单对象、{@code service.dto} 的接口对象，
 * 也有 {@link com.data.datafusion.service.apppackage.model.AppPackageContent} 这类纯 JSON 载体。
 * 由于这些类型只用于 JSON 编解码、不承载业务行为，统一采用 {@code public} 字段以保持清单结构
 * 与 JSON 一一对应、便于审阅；业务规则集中在 {@code AppPackageExportService} 与
 * {@code AppPackageImportService} 中。
 */
package com.data.datafusion.service.apppackage;
