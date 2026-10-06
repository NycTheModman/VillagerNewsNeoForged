package com.javafied.villagernews.molang;

import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ast.AccessExpression;
import team.unnamed.mocha.parser.ast.ArrayAccessExpression;
import team.unnamed.mocha.parser.ast.BinaryExpression;
import team.unnamed.mocha.parser.ast.CallExpression;
import team.unnamed.mocha.parser.ast.DoubleExpression;
import team.unnamed.mocha.parser.ast.ExecutionScopeExpression;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.parser.ast.IdentifierExpression;
import team.unnamed.mocha.parser.ast.TernaryConditionalExpression;
import team.unnamed.mocha.parser.ast.UnaryExpression;
import team.unnamed.mocha.runtime.ExpressionInterpreter;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * A parsed Molang program, run on unnamed/mocha's interpreter.
 *
 * <p>Only mocha's parser and interpreter are used, never {@code MochaEngine}:
 * the engine eagerly builds a javassist bytecode compiler we don't need (and
 * don't ship). Sources are parsed once and cached; one that fails to parse is
 * reported once and then evaluates to 0, like Bedrock does.
 */
public final class MolangProgram {
	private static final Map<String, MolangProgram> CACHE = new ConcurrentHashMap<>();
	private static final MolangProgram ZERO = new MolangProgram(List.of());
	private static final ObjectValue MATH = new BedrockMath();
	static final String EQUALS = "__villagernewsjavafied_equals";
	static final String NOT_EQUALS = "__villagernewsjavafied_not_equals";
	private static final Function<Object> EQUALS_FUNCTION = (ctx, args) -> compare(args.next().eval(), args.next().eval(), true);
	private static final Function<Object> NOT_EQUALS_FUNCTION = (ctx, args) -> compare(args.next().eval(), args.next().eval(), false);
	private static volatile Consumer<String> errorReporter = message -> {
	};

	private final List<Expression> expressions;

	private MolangProgram(List<Expression> expressions) {
		this.expressions = expressions;
	}

	public static void setErrorReporter(Consumer<String> reporter) {
		errorReporter = reporter;
	}

	public static MolangProgram of(String source) {
		return CACHE.computeIfAbsent(source, s -> {
			try {
				return new MolangProgram(MolangParser.parseAll(s).stream().map(MolangProgram::repair).toList());
			} catch (Exception e) {
				errorReporter.accept("Unparseable Molang (" + e.getMessage() + "): " + s);
				return ZERO;
			}
		});
	}

	/** A fresh scope with the standard {@code math} namespace; callers add v./q./t./texture. etc. */
	public static Scope newScope() {
		Scope scope = Scope.create();
		scope.set("math", MATH);
		scope.set(EQUALS, EQUALS_FUNCTION);
		scope.set(NOT_EQUALS, NOT_EQUALS_FUNCTION);
		return scope;
	}

	/**
	 * Bedrock equality: two strings compare as text, two non-strings
	 * numerically, and a string against anything else is false for both
	 * {@code ==} and {@code !=} (Bedrock refuses to compare mixed types). That
	 * last case matters: villagers start with {@code v.skwjdr=0} and leave their
	 * idle gesture state on {@code v.skwjdr!='default'} - were that true, they'd
	 * sit in the gesturing state forever and never play their walk cycle.
	 */
	private static Value compare(Value a, Value b, boolean equals) {
		boolean aString = a instanceof StringValue;
		if (aString != b instanceof StringValue) {
			return Value.of(false);
		}
		boolean equal = aString ? a.getAsString().equals(b.getAsString()) : a.getAsNumber() == b.getAsNumber();
		return Value.of(equal == equals);
	}

	/**
	 * Bedrock semantics: the value of a {@code return} if one runs (even from
	 * inside a {@code { }} block), else the value of a lone expression, else 0.
	 */
	public Value eval(Scope scope) {
		ExpressionInterpreter<Object> interpreter = new ExpressionInterpreter<>(null, scope);
		Value last = Value.nil();
		for (Expression expression : expressions) {
			last = expression.visit(interpreter);
			Value returned = interpreter.popReturnValue();
			if (returned != null) {
				return returned;
			}
		}
		return expressions.size() == 1 ? last : Value.nil();
	}

	public double evalNumber(Scope scope) {
		return eval(scope).getAsNumber();
	}

	public boolean evalBoolean(Scope scope) {
		return eval(scope).getAsBoolean();
	}

	/**
	 * Rewrites a parsed tree where mocha deviates from Bedrock Molang:
	 * <ul>
	 * <li>mocha parses {@code v.x = c ? a : b} as {@code (v.x = c) ? a : b}, so
	 * {@code v.x} ends up holding the condition (verified: {@code v.x = 0 < 15 ? 7 : 3}
	 * leaves v.x at 1). In Molang, as in C, assignment binds loosest. 35 of the
	 * add-on's expressions have this shape - including the ones picking the
	 * villager skin, biome hat and profession - so it becomes
	 * {@code v.x = (c ? a : b)} (likewise for the else-less {@code c ? a}).</li>
	 * <li>mocha compares strings by their numeric value (0), so
	 * {@code 'none' == 'ufernq'} is true. That hid every villager's hat bone -
	 * where most job outfits are. {@code ==}/{@code !=} become calls to
	 * {@link #EQUALS}/{@link #NOT_EQUALS}, bound in every {@link #newScope()}.</li>
	 * <li>mocha never runs the {@code { }} branches of {@code c ? {...} : {...}}
	 * (19 of the add-on's scripts - whether a villager holds a sign, its
	 * dying pose, some secondary motion); see {@link #runBlock}.</li>
	 * </ul>
	 */
	static Expression repair(Expression e) {
		if (e instanceof TernaryConditionalExpression t) {
			Expression condition = repair(t.condition());
			Expression whenTrue = repair(t.trueExpression());
			Expression whenFalse = repair(t.falseExpression());
			if (condition instanceof BinaryExpression assign && assign.op() == BinaryExpression.Op.ASSIGN) {
				return new BinaryExpression(BinaryExpression.Op.ASSIGN, assign.left(),
						new TernaryConditionalExpression(assign.right(), runBlock(whenTrue), runBlock(whenFalse)));
			}
			return new TernaryConditionalExpression(condition, runBlock(whenTrue), runBlock(whenFalse));
		}
		if (e instanceof BinaryExpression b) {
			Expression left = repair(b.left());
			Expression right = repair(b.right());
			if (b.op() == BinaryExpression.Op.CONDITIONAL
					&& left instanceof BinaryExpression assign && assign.op() == BinaryExpression.Op.ASSIGN) {
				return new BinaryExpression(BinaryExpression.Op.ASSIGN, assign.left(),
						new BinaryExpression(BinaryExpression.Op.CONDITIONAL, assign.right(), right));
			}
			if (b.op() == BinaryExpression.Op.EQ || b.op() == BinaryExpression.Op.NEQ) {
				return new CallExpression(new IdentifierExpression(b.op() == BinaryExpression.Op.EQ ? EQUALS : NOT_EQUALS),
						List.of(left, right));
			}
			return new BinaryExpression(b.op(), left, right);
		}
		if (e instanceof UnaryExpression u) {
			return new UnaryExpression(u.op(), repair(u.expression()));
		}
		if (e instanceof ExecutionScopeExpression scope) {
			return new ExecutionScopeExpression(scope.expressions().stream().map(MolangProgram::repair).toList());
		}
		if (e instanceof CallExpression call) {
			return new CallExpression(repair(call.function()),
					call.arguments().stream().map(MolangProgram::repair).toList());
		}
		if (e instanceof ArrayAccessExpression access) {
			return new ArrayAccessExpression(repair(access.array()), repair(access.index()));
		}
		if (e instanceof AccessExpression access) {
			return new AccessExpression(repair(access.object()), access.property());
		}
		return e;
	}

	/**
	 * mocha evaluates a {@code { }} block to a function and only runs it as the
	 * body of {@code c ? {...}}; as a branch of {@code c ? {...} : {...}} it is
	 * never run, so neither branch happens. {@code 1 ? {...}} runs it the way
	 * the working form does (a {@code return} inside still returns).
	 */
	private static Expression runBlock(Expression branch) {
		return branch instanceof ExecutionScopeExpression
				? new BinaryExpression(BinaryExpression.Op.CONDITIONAL, DoubleExpression.ONE, branch)
				: branch;
	}

	/**
	 * mocha's standard math, except {@code random_integer}: mocha uses
	 * {@code nextInt(min, max)}, which excludes {@code max} (and throws when
	 * they're equal), while Bedrock's is inclusive on both ends - so e.g. the
	 * add-on's {@code Math.random_integer(0,1)} could never return 1.
	 */
	private static final class BedrockMath implements ObjectValue {
		private final ObjectValue standard = JavaObjectBinding.of(MochaMath.class, null, new MochaMath());
		private final ObjectProperty randomInteger = ObjectProperty.property((Function<Object>) (ctx, args) -> {
			int a = (int) args.next().eval().getAsNumber();
			int b = (int) args.next().eval().getAsNumber();
			return Value.of(ThreadLocalRandom.current().nextInt(Math.min(a, b), Math.max(a, b) + 1));
		}, true);

		@Override
		public ObjectProperty getProperty(String name) {
			return name.equalsIgnoreCase("random_integer") ? randomInteger : standard.getProperty(name);
		}
	}
}
