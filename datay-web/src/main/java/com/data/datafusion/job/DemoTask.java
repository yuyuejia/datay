package com.data.datafusion.job;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoTask extends AbstractTask {

    private final Logger log = LoggerFactory.getLogger(DemoTask.class);

    public DemoTask(JobInstance jobInstance) {
        super(jobInstance);
    }

    @Override
    public String doExecute() throws Exception {
        Thread.sleep(1000);
        log.info("###DEMO任务执行完成###");
        return "demo";
    }
}
