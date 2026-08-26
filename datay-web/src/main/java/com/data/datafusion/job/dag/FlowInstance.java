package com.data.datafusion.job.dag;

import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAG工作流
 */
public class FlowInstance {

    private Set<JobInstance> jobInstances;
    private List<JobDepend> jobDepends;

    public FlowInstance() {
        this.jobInstances = new HashSet<JobInstance>();
        this.jobDepends = new ArrayList<JobDepend>();
    }

    public Set<JobInstance> getJobInstances() {
        return jobInstances;
    }

    public void setJobInstances(Set<JobInstance> jobs) {
        this.jobInstances = jobs;
    }

    public List<JobDepend> getJobDepends() {
        return jobDepends;
    }

    public void setJobDepends(List<JobDepend> jobDepends) {
        this.jobDepends = jobDepends;
    }
}
