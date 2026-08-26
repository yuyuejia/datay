package com.data.datafusion.job;

public interface ITask {
    String execute();

    boolean cancel();
}
