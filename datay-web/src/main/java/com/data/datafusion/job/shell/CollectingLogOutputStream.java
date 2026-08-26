package com.data.datafusion.job.shell;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.exec.ExecuteStreamHandler;

public class CollectingLogOutputStream implements ExecuteStreamHandler {

    private final List<String> lines = new LinkedList<String>();

    private OutputStream out;

    //    private final OutputStream err;
    public void setProcessInputStream(OutputStream outputStream) throws IOException {
        //        this.out = outputStream;
    }

    public CollectingLogOutputStream(OutputStream out) {
        this.out = out;
    }

    //important - read all output line by line to track errors
    public void setProcessErrorStream(InputStream inputStream) throws IOException {
        InputStreamReader isr = new InputStreamReader(inputStream, Charset.forName("GBK"));
        BufferedReader br = new BufferedReader(isr);
        String line = "";
        while ((line = br.readLine()) != null) {
            //use lines whereever you want - for now just print on console
            //            System.out.println("error:" + line);
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    //important - read all output line by line to track process output
    public void setProcessOutputStream(InputStream inputStream) throws IOException {
        InputStreamReader isr = new InputStreamReader(inputStream, Charset.forName("GBK"));
        BufferedReader br = new BufferedReader(isr);
        String line = "";
        while ((line = br.readLine()) != null) {
            //use lines whereever you want - for now just print on console
            //            System.out.println("output:" + line);
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    public void start() throws IOException {}

    public void stop() throws IOException {}
}
