grammar Parameter;

// 主规则：解析所有参数
parameters : (parameter | TEXT)* EOF ;

// 参数规则：匹配三种类型的参数
parameter
    : dollarCurly           # DollarCurlyParam    // ${} 类型
    | dollarCurlyAttr       # DollarCurlyAttrParam // ${attr:} 类型
    | hashCurly             # HashCurlyParam      // #{} 类型
    ;

// ${} 类型参数，例如 ${name}
dollarCurly : '${' ID '}';

// ${attr:} 类型参数，例如 ${attr:name}
dollarCurlyAttr : '${attr:' ID '}';

// #{} 类型参数，例如 #{name}
hashCurly : '#{' ID '}';

// 标识符规则：参数名由字母、数字和下划线组成
ID : [a-zA-Z_][a-zA-Z0-9_]*;

TEXT: ~[${#}]+;
// 忽略空白字符
WS : [ \t\r\n]+ -> skip;
