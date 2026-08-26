package com.data.datafusion.job;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.impl.MysqlConverter;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLDouble;
import com.jmatio.types.MLStructure;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MatlabTest {

    @Test
    public void testLoadMat() {
        try {
            // 读取MAT文件
            String filePath = "D:/aaa.mat";
            MatFileReader matFileReader = new MatFileReader(filePath);
            // 获取MAT文件中的数据
            Map<String, MLArray> content = matFileReader.getContent();
            // 遍历数据并打印
            for (Map.Entry<String, MLArray> entry : content.entrySet()) {
                System.out.println("Variable: " + entry.getKey());
                MLArray array = entry.getValue();
                if (array instanceof MLStructure) {
                    MLStructure mlStructure = (MLStructure) array;
                    Collection<String> fieldNames = mlStructure.getFieldNames();
                    for (String fieldName : fieldNames) {
                        MLArray field = mlStructure.getField(fieldName);
                        System.out.println(fieldName + " : " + field);
                        if (field instanceof MLDouble) {
                            MLDouble mlDouble = (MLDouble) field;
                            double[][] data = mlDouble.getArray();
                            for (double[] row : data) {
                                for (double value : row) {
                                    System.out.print(value + " ");
                                }
                                System.out.println();
                            }
                        }
                    }
                    //                    System.out.println(fieldNames);
                    //                    Collection<MLArray> fields = mlStructure.getAllFields();
                    //                    for (MLArray field : fields) {
                    //                        System.out.println("Variable: " + field.getName());
                    //                        if (field instanceof MLDouble) {
                    //                            MLDouble mlDouble = (MLDouble) field;
                    //                            double[][] data = mlDouble.getArray();
                    //                            for (double[] row : data) {
                    //                                for (double value : row) {
                    //                                    System.out.print(value + " ");
                    //                                }
                    //                                System.out.println();
                    //                            }
                    //                        }
                    //                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
