package com.data.datafusion.job.sql;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.metadata.util.DBUtils;
import java.io.*;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mybatis执行SQL脚本
 */
public class SqlTask extends AbstractTask {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private DataSource dataSource;

    public SqlTask(JobInstance taskInstance) {
        super(taskInstance);
    }

    /**
     * 使用ScriptRunner执行SQL脚本
     */
    public String doExecute() throws Exception {
        Map<String, String> properties = JSONObject.parseObject(getJobInstance().getJobContext(), Map.class);
        //通过数据源获取数据库链接
        //        Connection connection = DataSourceUtils.getConnection(dataSource);
        Connection connection = DBUtils.getConnection();
        //创建脚本执行器
        ScriptRunner scriptRunner = new ScriptRunner(connection);
        //创建字符输出流，用于记录SQL执行日志
        StringWriter writer = new StringWriter();
        PrintWriter print = new PrintWriter(System.out);
        //设置执行器日志输出
        scriptRunner.setLogWriter(print);
        //设置执行器错误日志输出
        scriptRunner.setErrorLogWriter(print);
        //设置读取文件格式
        //        Resources.setCharset(Charsets.UTF_8);
        //        FileUtils.writeStringToFile("select 1;", getSqlFile());
        Reader reader = null;
        try {
            //获取资源文件的字符输入流
            reader = new StringReader("select 1;select 1;");
        } catch (Exception e) {
            //文件流获取失败，关闭链接
            logger.error(e.getMessage(), e);
            scriptRunner.closeConnection();
            throw new Exception("job exec error!");
        }
        //执行SQL脚本
        scriptRunner.runScript(reader);
        //关闭文件输入流
        try {
            reader.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        //输出SQL执行日志
        //        logger.debug(writer.toString());
        //关闭输入流
        scriptRunner.closeConnection();
        return "0";
    }

    private String getSqlFile() {
        String fileName = "./log/" + getJobInstance().getJobCode() + "/" + getJobInstance().getInstanceCode() + ".sql";
        return fileName;
    }
}