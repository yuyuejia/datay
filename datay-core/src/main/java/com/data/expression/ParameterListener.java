package com.data.expression;

import com.data.expression.gen.ParameterBaseListener;
import com.data.expression.gen.ParameterParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class ParameterListener extends ParameterBaseListener {

    private List<ParameterInfo> parameters = new ArrayList<>();

    @Override
    public void enterDollarCurly(ParameterParser.DollarCurlyContext ctx) {
        String paramName = ctx.ID().getText();
        parameters.add(new ParameterInfo(paramName, ParameterType.DOLLAR_CURLY));
    }

    @Override
    public void enterDollarCurlyAttr(ParameterParser.DollarCurlyAttrContext ctx) {
        String paramName = ctx.ID().getText();
        parameters.add(new ParameterInfo(paramName, ParameterType.DOLLAR_CURLY_ATTR));
    }

    @Override
    public void enterHashCurly(ParameterParser.HashCurlyContext ctx) {
        String paramName = ctx.ID().getText();
        parameters.add(new ParameterInfo(paramName, ParameterType.HASH_CURLY));
    }

    // 其他未使用的方法可以保持默认实现
    @Override
    public void exitDollarCurly(ParameterParser.DollarCurlyContext ctx) {}

    @Override
    public void exitDollarCurlyAttr(ParameterParser.DollarCurlyAttrContext ctx) {}

    @Override
    public void exitHashCurly(ParameterParser.HashCurlyContext ctx) {}

    @Override
    public void visitTerminal(TerminalNode node) {}

    public List<ParameterInfo> getParameters() {
        return parameters;
    }
}
