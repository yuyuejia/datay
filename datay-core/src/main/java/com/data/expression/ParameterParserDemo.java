package com.data.expression;

import com.data.expression.gen.ParameterLexer;
import com.data.expression.gen.ParameterParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.List;

public class ParameterParserDemo {

    public static void main(String[] args) {
        String text = "Hello ${name}, your age is #{age}. " + "Your email is ${attr:email} and address is ${address}.";

        // 创建ANTLR输入流
        CharStream input = CharStreams.fromString(text);

        // 创建词法分析器
        ParameterLexer lexer = new ParameterLexer(input);

        // 创建词法符号流
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // 创建语法分析器
        ParameterParser parser = new ParameterParser(tokens);

        // 解析输入，获取语法树
        ParseTree tree = parser.parameters();

        // 创建并应用监听器
        ParameterListener listener = new ParameterListener();
        ParseTreeWalker.DEFAULT.walk(listener, tree);

        // 获取并打印解析结果
        List<ParameterInfo> parameters = listener.getParameters();
        System.out.println("解析到的参数:");
        for (ParameterInfo param : parameters) {
            System.out.println(param);
        }
    }
}
