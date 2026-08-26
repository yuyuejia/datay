grammar Parameter;

// 主规则：解析所有参数
parameters : (parameter | .)* EOF ;

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
ID : [a-zA-Z0-9_][a-zA-Z0-9_\-+:, .]*;

// TEXT规则：必须以${或#{开头，后面可以跟任何字符
//TEXT: ('${' | '#{') .*?;
//TEXT: ~[${#}]+;
//TEXT : ( ~[$#] | '$' ~'{' | '#' ~'{' )+ ;
//TEXT : ( ~[${#] | '$' ~'{' | '#' ~'{' )+ ;
//TEXT : ( ~[$#]          // 匹配除了 $ 和 # 之外的任意字符
//      | '$' ~'{'       // 匹配 $ 但后面不是 {
//      | '#' ~'{'       // 匹配 # 但后面不是 {
//      )+ ;             // 匹配一个或多个上述字符
// 忽略空白字符
WS : [ \t\r\n]+ -> skip;

// 匹配任意其他字符（非参数部分的文本）
ANY : .;
