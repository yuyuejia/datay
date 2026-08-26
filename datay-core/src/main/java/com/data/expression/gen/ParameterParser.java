// Generated from /Users/chenjie/code/yuyuejia/datay/src/main/antlr4/Parameter.g4 by ANTLR 4.13.2
package com.data.expression.gen;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class ParameterParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, ID=5, WS=6, ANY=7;
	public static final int
		RULE_parameters = 0, RULE_parameter = 1, RULE_dollarCurly = 2, RULE_dollarCurlyAttr = 3, 
		RULE_hashCurly = 4;
	private static String[] makeRuleNames() {
		return new String[] {
			"parameters", "parameter", "dollarCurly", "dollarCurlyAttr", "hashCurly"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'${'", "'}'", "'${attr:'", "'#{'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, "ID", "WS", "ANY"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Parameter.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ParameterParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParametersContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(ParameterParser.EOF, 0); }
		public List<ParameterContext> parameter() {
			return getRuleContexts(ParameterContext.class);
		}
		public ParameterContext parameter(int i) {
			return getRuleContext(ParameterContext.class,i);
		}
		public ParametersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameters; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterParameters(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitParameters(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitParameters(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParametersContext parameters() throws RecognitionException {
		ParametersContext _localctx = new ParametersContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parameters);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(14);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 254L) != 0)) {
				{
				setState(12);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
				case 1:
					{
					setState(10);
					parameter();
					}
					break;
				case 2:
					{
					setState(11);
					matchWildcard();
					}
					break;
				}
				}
				setState(16);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(17);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterContext extends ParserRuleContext {
		public ParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameter; }
	 
		public ParameterContext() { }
		public void copyFrom(ParameterContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DollarCurlyParamContext extends ParameterContext {
		public DollarCurlyContext dollarCurly() {
			return getRuleContext(DollarCurlyContext.class,0);
		}
		public DollarCurlyParamContext(ParameterContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterDollarCurlyParam(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitDollarCurlyParam(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitDollarCurlyParam(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DollarCurlyAttrParamContext extends ParameterContext {
		public DollarCurlyAttrContext dollarCurlyAttr() {
			return getRuleContext(DollarCurlyAttrContext.class,0);
		}
		public DollarCurlyAttrParamContext(ParameterContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterDollarCurlyAttrParam(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitDollarCurlyAttrParam(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitDollarCurlyAttrParam(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class HashCurlyParamContext extends ParameterContext {
		public HashCurlyContext hashCurly() {
			return getRuleContext(HashCurlyContext.class,0);
		}
		public HashCurlyParamContext(ParameterContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterHashCurlyParam(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitHashCurlyParam(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitHashCurlyParam(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterContext parameter() throws RecognitionException {
		ParameterContext _localctx = new ParameterContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_parameter);
		try {
			setState(22);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__0:
				_localctx = new DollarCurlyParamContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(19);
				dollarCurly();
				}
				break;
			case T__2:
				_localctx = new DollarCurlyAttrParamContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(20);
				dollarCurlyAttr();
				}
				break;
			case T__3:
				_localctx = new HashCurlyParamContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(21);
				hashCurly();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DollarCurlyContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ParameterParser.ID, 0); }
		public DollarCurlyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dollarCurly; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterDollarCurly(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitDollarCurly(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitDollarCurly(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DollarCurlyContext dollarCurly() throws RecognitionException {
		DollarCurlyContext _localctx = new DollarCurlyContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_dollarCurly);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(24);
			match(T__0);
			setState(25);
			match(ID);
			setState(26);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DollarCurlyAttrContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ParameterParser.ID, 0); }
		public DollarCurlyAttrContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dollarCurlyAttr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterDollarCurlyAttr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitDollarCurlyAttr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitDollarCurlyAttr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DollarCurlyAttrContext dollarCurlyAttr() throws RecognitionException {
		DollarCurlyAttrContext _localctx = new DollarCurlyAttrContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_dollarCurlyAttr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(28);
			match(T__2);
			setState(29);
			match(ID);
			setState(30);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class HashCurlyContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ParameterParser.ID, 0); }
		public HashCurlyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_hashCurly; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).enterHashCurly(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ParameterListener ) ((ParameterListener)listener).exitHashCurly(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ParameterVisitor ) return ((ParameterVisitor<? extends T>)visitor).visitHashCurly(this);
			else return visitor.visitChildren(this);
		}
	}

	public final HashCurlyContext hashCurly() throws RecognitionException {
		HashCurlyContext _localctx = new HashCurlyContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_hashCurly);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(32);
			match(T__3);
			setState(33);
			match(ID);
			setState(34);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0007%\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0001"+
		"\u0000\u0001\u0000\u0005\u0000\r\b\u0000\n\u0000\f\u0000\u0010\t\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001"+
		"\u0017\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0000\u0000\u0005\u0000\u0002\u0004\u0006\b\u0000"+
		"\u0000#\u0000\u000e\u0001\u0000\u0000\u0000\u0002\u0016\u0001\u0000\u0000"+
		"\u0000\u0004\u0018\u0001\u0000\u0000\u0000\u0006\u001c\u0001\u0000\u0000"+
		"\u0000\b \u0001\u0000\u0000\u0000\n\r\u0003\u0002\u0001\u0000\u000b\r"+
		"\t\u0000\u0000\u0000\f\n\u0001\u0000\u0000\u0000\f\u000b\u0001\u0000\u0000"+
		"\u0000\r\u0010\u0001\u0000\u0000\u0000\u000e\f\u0001\u0000\u0000\u0000"+
		"\u000e\u000f\u0001\u0000\u0000\u0000\u000f\u0011\u0001\u0000\u0000\u0000"+
		"\u0010\u000e\u0001\u0000\u0000\u0000\u0011\u0012\u0005\u0000\u0000\u0001"+
		"\u0012\u0001\u0001\u0000\u0000\u0000\u0013\u0017\u0003\u0004\u0002\u0000"+
		"\u0014\u0017\u0003\u0006\u0003\u0000\u0015\u0017\u0003\b\u0004\u0000\u0016"+
		"\u0013\u0001\u0000\u0000\u0000\u0016\u0014\u0001\u0000\u0000\u0000\u0016"+
		"\u0015\u0001\u0000\u0000\u0000\u0017\u0003\u0001\u0000\u0000\u0000\u0018"+
		"\u0019\u0005\u0001\u0000\u0000\u0019\u001a\u0005\u0005\u0000\u0000\u001a"+
		"\u001b\u0005\u0002\u0000\u0000\u001b\u0005\u0001\u0000\u0000\u0000\u001c"+
		"\u001d\u0005\u0003\u0000\u0000\u001d\u001e\u0005\u0005\u0000\u0000\u001e"+
		"\u001f\u0005\u0002\u0000\u0000\u001f\u0007\u0001\u0000\u0000\u0000 !\u0005"+
		"\u0004\u0000\u0000!\"\u0005\u0005\u0000\u0000\"#\u0005\u0002\u0000\u0000"+
		"#\t\u0001\u0000\u0000\u0000\u0003\f\u000e\u0016";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}