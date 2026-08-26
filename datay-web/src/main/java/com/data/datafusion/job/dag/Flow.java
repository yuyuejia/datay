package com.data.datafusion.job.dag;

import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobDepend;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAG工作流
 */
public class Flow {

    private Set<Job> jobs;
    private List<JobDepend> jobDepends;

    public Flow() {
        this.jobs = new HashSet<Job>();
        this.jobDepends = new ArrayList<JobDepend>();
    }

    public Set<Job> getJobs() {
        return jobs;
    }

    public void setJobs(Set<Job> jobs) {
        this.jobs = jobs;
    }

    public List<JobDepend> getJobDepends() {
        return jobDepends;
    }

    public void setJobDepends(List<JobDepend> jobDepends) {
        this.jobDepends = jobDepends;
    }
}
