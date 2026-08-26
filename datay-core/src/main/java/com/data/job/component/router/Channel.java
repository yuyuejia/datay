package com.data.job.component.router;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

public class Channel extends FlowComponent {


    public Channel() {
            setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        String event = flowFile.getAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE).toString();
        if("INSERT".equals(event)){

        }else if("UPDATE".equals(event)){
            //如果是 update 事件，获取 before 数据
            JSONArray records = flowFile.getJsonArray();
            for (Object record : records) {
                JSONObject jsonRecord = (JSONObject) record;
                JSONObject beforeRecord = jsonRecord.getJSONObject("__before");
                Double order_amount = beforeRecord.getDouble("order_amount");
                //如果是删除事件，金额字段加负号
                if(order_amount == null){
                    beforeRecord.put("order_amount", -1 * order_amount);
                }
                records.add(beforeRecord);
            }
        }else if("DELETE".equals(event)){
            JSONArray records = flowFile.getJsonArray();
            for (Object record : records) {
                JSONObject jsonRecord = (JSONObject) record;
                Double order_amount = jsonRecord.getDouble("order_amount");
                //如果是删除事件，金额字段加负号
                if(order_amount == null){
                    jsonRecord.put("order_amount", -1 * order_amount);
                }
            }
        }
        writeRecords(flowFile);
    }
}
