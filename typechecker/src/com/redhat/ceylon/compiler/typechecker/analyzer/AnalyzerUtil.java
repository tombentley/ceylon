package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.formatPath;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.hasError;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.name;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.unwrapExpressionUntilTerm;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.appliedType;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.intersectionType;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isConstructor;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isForBackend;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isNamed;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.isTypeUnknown;
import static com.redhat.ceylon.model.typechecker.model.ModelUtil.unionType;
import static com.redhat.ceylon.model.typechecker.model.SiteVariance.IN;
import static com.redhat.ceylon.model.typechecker.model.SiteVariance.OUT;
import static java.lang.Character.isUpperCase;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ClassBody;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypeVariance;
import com.redhat.ceylon.compiler.typechecker.util.NormalizedLevenshtein;
import com.redhat.ceylon.model.loader.JvmBackendUtil;
import com.redhat.ceylon.model.loader.NamingBase;
import com.redhat.ceylon.model.typechecker.model.Cancellable;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.Generic;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.Module;
import com.redhat.ceylon.model.typechecker.model.ModuleImport;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ParameterList;
import com.redhat.ceylon.model.typechecker.model.Reference;
import com.redhat.ceylon.model.typechecker.model.Scope;
import com.redhat.ceylon.model.typechecker.model.SiteVariance;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeAlias;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypeParameter;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedReference;
import com.redhat.ceylon.model.typechecker.model.Unit;
import com.redhat.ceylon.model.typechecker.model.Value;

/**
 * Bucket for some helper methods used by various
 * visitors.
 * 
 * @author Gavin King
 *
 */
public class AnalyzerUtil {
    
//    static final JaroWinkler distance = new JaroWinkler();
    static final NormalizedLevenshtein distance = 
            new NormalizedLevenshtein();
    
    static final List<Type> NO_TYPE_ARGS = emptyList();
    
    static TypedDeclaration getTypedMember(TypeDeclaration td, 
            String name, List<Type> signature, boolean ellipsis, 
            Unit unit, Scope scope) {
        Declaration member = 
                td.getMember(name, unit, signature, ellipsis);
        if (member instanceof TypedDeclaration) {
            return (TypedDeclaration) member;
        }
        else {
            if (td.isJava()
                    && isForBackend(scope.getScopedBackends(), Backend.Java) 
                    && !JvmBackendUtil.isInitialLowerCase(name)) {
                name = NamingBase.getJavaBeanName(name);
                member = td.getMember(name, unit, signature, ellipsis);
                if (member instanceof TypedDeclaration) {
                    return (TypedDeclaration) member;
                }
            }
            return null;
        }
    }

    static TypeDeclaration getTypeMember(TypeDeclaration td, 
            String name, List<Type> signature, boolean ellipsis, 
            Unit unit, Scope scope) {
        Declaration member = 
                td.getMember(name, unit, signature, ellipsis);
        if (member instanceof TypeDeclaration) {
            return (TypeDeclaration) member;
        }
        else if (member instanceof TypedDeclaration) {
            return anonymousType(name, 
                    (TypedDeclaration) member);
        }
        else {
            if (td.isJava()
                    && isForBackend(scope.getScopedBackends(), Backend.Java) 
                    && JvmBackendUtil.isInitialLowerCase(name)) {
                name = NamingBase.capitalize(name);
                member = 
                        td.getMember(name, unit, signature, ellipsis);
                if (member instanceof TypeDeclaration) {
                    return (TypeDeclaration) member;
                }
                else if (member instanceof TypedDeclaration) {
                    return anonymousType(name, 
                            (TypedDeclaration) member);
                }
            }
            return null;
        }
    }

    static TypedDeclaration getTypedDeclaration(Scope scope,
            String name, List<Type> signature, boolean ellipsis,
            Unit unit) {
        Declaration result = 
                scope.getMemberOrParameter(unit, 
                        name, signature, ellipsis);
        if (result instanceof TypedDeclaration) {
            return (TypedDeclaration) result;
        }
        else {
            if (isForBackend(scope.getScopedBackends(), Backend.Java) 
                    && !JvmBackendUtil.isInitialLowerCase(name)) {
                name = NamingBase.getJavaBeanName(name);
                // This method is used for base members, not qualified members
                // so we don't look for members but we look on the scope, which
                // may contain values with the modified case. For example, given
                // import Foo { ... } which contains a \iBAR we map to "bar",
                // if we look on the scope for "bar" we may find one that is not
                // the one we wanted to import, so try imports first.
                result = unit.getImportedDeclaration(name, 
                        signature, ellipsis);
                if(result != null && !result.isJava()){
                    result = null;
                }else if(result instanceof TypedDeclaration)
                    return (TypedDeclaration) result;
                result = 
                        scope.getMemberOrParameter(unit, 
                                name, signature, ellipsis);
                if(result != null && !result.isJava()){
                    result = null;
                }else if (result instanceof TypedDeclaration) {
                    return (TypedDeclaration) result;
                }
            }
            return null;
        }
    }
    
    static TypeDeclaration getTypeDeclaration(Scope scope,
            String name, List<Type> signature, boolean ellipsis,
            Unit unit) {
        Declaration result = 
                scope.getMemberOrParameter(unit, 
                        name, signature, ellipsis);
        if (result instanceof TypeDeclaration) {
        	return (TypeDeclaration) result;
        }
        else if (result instanceof TypedDeclaration) {
            return anonymousType(name, 
                    (TypedDeclaration) result);
        }
        else {
            if (isForBackend(scope.getScopedBackends(), Backend.Java) 
                    && JvmBackendUtil.isInitialLowerCase(name)) {
                name = NamingBase.capitalize(name);
                // This method is used for base members, not qualified members
                // so we don't look for members but we look on the scope, which
                // may contain values with the modified case. For example, given
                // import Foo { ... } which contains a \iBAR we map to "bar",
                // if we look on the scope for "bar" we may find one that is not
                // the one we wanted to import, so try imports first.
                result = unit.getImportedDeclaration(name, 
                        signature, ellipsis);
                if(result != null && !result.isJava()){
                    result = null;
                }else if (result instanceof TypeDeclaration) {
                    return (TypeDeclaration) result;
                }
                else if (result instanceof TypedDeclaration) {
                    return anonymousType(name, 
                            (TypedDeclaration) result);
                }
                result = 
                        scope.getMemberOrParameter(unit, 
                                name, signature, ellipsis);
                if(result != null && !result.isJava()){
                    result = null;
                }else if (result instanceof TypeDeclaration) {
                    return (TypeDeclaration) result;
                }
                else if (result instanceof TypedDeclaration) {
                    return anonymousType(name, 
                            (TypedDeclaration) result);
                }
            }
        	return null;
        }
    }
    
    static TypedDeclaration getPackageTypedDeclaration(
            String name, List<Type> signature, boolean ellipsis,
            Unit unit) {
        Declaration result = 
                unit.getPackage()
                    .getMember(name, signature, ellipsis);
        if (result instanceof TypedDeclaration) {
            return (TypedDeclaration) result;
        }
        else {
            return null;
        }
    }
    
    static TypeDeclaration getPackageTypeDeclaration(
            String name, List<Type> signature, boolean ellipsis,
            Unit unit) {
        Declaration result = 
                unit.getPackage()
                    .getMember(name, signature, ellipsis);
        if (result instanceof TypeDeclaration) {
            return (TypeDeclaration) result;
        }
        else if (result instanceof TypedDeclaration) {
            return anonymousType(name, 
                    (TypedDeclaration) result);
        }
        else {
            return null;
        }
    }

    private static TypeDeclaration anonymousType(String name, 
            TypedDeclaration result) {
        Type type = result.getType();
        if (type!=null) {
            TypeDeclaration typeDeclaration = 
                    type.getDeclaration();
            if ((typeDeclaration instanceof Class ||
                typeDeclaration instanceof Constructor) &&
                    typeDeclaration.isAnonymous() &&
                    isNamed(name, typeDeclaration)) {
                return typeDeclaration;
            }
        }
        return null;
    }
    
    private static DeclarationWithProximity best(final String name, 
            Map<String,DeclarationWithProximity> suggestions) {
        suggestions.remove(name); //don't ever suggest the name itself
        if (suggestions.isEmpty()) {
            return null;
        }
        final boolean ucase = isUpperCase(name.charAt(0));
        DeclarationWithProximity best = 
                Collections.max(suggestions.values(), 
                new Comparator<DeclarationWithProximity>() {
            @Override
            public int compare(DeclarationWithProximity xe, 
                               DeclarationWithProximity ye) {
                //don't use the keys because for 
                //unimported declarations they are
                //qualified names!
                String x = xe.getName();
                String y = ye.getName();
                boolean xucase = isUpperCase(x.charAt(0));
                boolean yucase = isUpperCase(y.charAt(0));
                if (ucase==xucase && ucase!=yucase) {
                    return 1;
                }
                if (ucase==yucase && ucase!=xucase) {
                    return -1;
                }
                int comp = Double.compare(
                        distance.similarity(name, x),
                        distance.similarity(name, y));
                if (comp==0) {
                    comp = - Integer.compare(
                        xe.getProximity(), 
                        ye.getProximity());
                }
                return comp;
            }
        });
        return distance.similarity(name, best.getName()) > 0.5 ? 
                best : null;
    }
    
    private static DeclarationWithProximity correct(
            Scope scope, Unit unit, String name, 
            Cancellable canceller) {
        if (canceller != null 
                && canceller.isCancelled()) {
            return null;
        }
        return best(name, 
                scope.getMatchingDeclarations(unit, 
                        //pass a prefix here, or otherwise
                        //it won't search all importable
                        //packages
                        name.substring(0, 1), 
                        0, canceller));
    }

    private static DeclarationWithProximity correct(
            Package scope, String name,
            Cancellable canceller) {
        if (canceller != null 
                && canceller.isCancelled()) {
            return null;
        }
        return best(name, 
                scope.getMatchingDirectDeclarations(
                        "", 0, canceller));
    }

    private static DeclarationWithProximity correct(
            TypeDeclaration type, Scope scope, Unit unit, String name, 
            Cancellable canceller) {
        if (canceller != null 
                && canceller.isCancelled()) {
            return null;
        }
        return best(name,
                type.getMatchingMemberDeclarations(unit, 
                        scope, "", 0, canceller));
    }
    
    static List<SiteVariance> getVariances(
            Tree.TypeArguments tas,
            List<TypeParameter> typeParameters) {
        if (tas instanceof Tree.TypeArgumentList) {
            Tree.TypeArgumentList tal = 
                    (Tree.TypeArgumentList) tas;
            int size = typeParameters.size();
            List<SiteVariance> variances = 
                    new ArrayList<SiteVariance>(size);
            List<Tree.Type> types = tal.getTypes();
            int count = types.size();
            for (int i=0; i<count; i++) {
                Tree.Type type = types.get(i);
                if (type instanceof Tree.StaticType) {
                    Tree.StaticType st = 
                            (Tree.StaticType) type;
                    TypeVariance tv = 
                            st.getTypeVariance();
                    if (tv!=null) {
                        boolean contra = 
                                tv.getText()
                                  .equals("in");
                        variances.add(contra?IN:OUT);
                    }
                    else {
                        variances.add(null);
                    }
                }
                else {
                    variances.add(null);
                }
            }
            return variances;
        }
        else {
            return emptyList();
        }
    }
    
    /**
     * Get the type arguments specified explicitly in the
     * code, or an empty list if no type arguments were
     * explicitly specified. For missing arguments, use
     * default type arguments.
     * 
     * @param tas the type argument list
     * @param qualifyingType the qualifying type
     * @param typeParameters the list of type parameters
     * 
     * @return a list of type arguments to the given type
     *         parameters
     */
    static List<Type> getTypeArguments(
            Tree.TypeArguments tas,
    		Type qualifyingType, 
    		List<TypeParameter> typeParameters) {
        if (tas instanceof Tree.TypeArgumentList) {
            
            //accumulate substitutions in case we need
            //them below for calculating default args
            Map<TypeParameter,Type> typeArgs = 
                    new HashMap<TypeParameter,Type>();
            Map<TypeParameter,SiteVariance> vars = 
                    new HashMap<TypeParameter,SiteVariance>();
            if (qualifyingType!=null) {
                typeArgs.putAll(qualifyingType.getTypeArguments());
                vars.putAll(qualifyingType.getVarianceOverrides());
            }
            
            Tree.TypeArgumentList tal = 
                    (Tree.TypeArgumentList) tas;
            int size = typeParameters.size();
            List<Type> typeArguments = 
                    new ArrayList<Type>(size);
            List<Tree.Type> types = tal.getTypes();
            int count = types.size();
            for (int i=0; i<count; i++) {
                Tree.Type type = types.get(i);
                Type t = type.getTypeModel();
                if (t==null) {
                    typeArguments.add(null);
                }
                else {
                    typeArguments.add(t);
                    if (i<size) {
                        TypeParameter tp = 
                                typeParameters.get(i);
                        if (tp.isTypeConstructor()) {
                            setTypeConstructor(type, tp);
                        }
                        typeArgs.put(tp, t);
                        if (type instanceof Tree.StaticType) {
                            Tree.StaticType st = 
                                    (Tree.StaticType) type;
                            TypeVariance tv = 
                                    st.getTypeVariance();
                            if (tv!=null) {
                                boolean contra = 
                                        tv.getText()
                                          .equals("in");
                                vars.put(tp, contra?IN:OUT);
                            }
                        }
                    }
                }
            }
            
            //for missing arguments, use the default args
            for (int i=typeArguments.size(); i<size; i++) {
                TypeParameter tp = typeParameters.get(i);
                Type dta = tp.getDefaultTypeArgument();
                if (dta==null) {
                    break;
                }
                else {
                    Type da = dta.substitute(typeArgs, vars);
                    typeArguments.add(da);
                    typeArgs.put(tp, da);
                }
            }
            
            return typeArguments;
        }
        else {
            return emptyList();
        }
    }
    
    public static Tree.Statement getLastExecutableStatement(
            Tree.ClassBody that) {
        List<Tree.Statement> statements = 
                that.getStatements();
        Unit unit = that.getUnit();
        for (int i=statements.size()-1; i>=0; i--) {
            Tree.Statement s = statements.get(i);
            if (isExecutableStatement(unit, s) || 
                    s instanceof Tree.Constructor ||
                    s instanceof Tree.Enumerated) {
                return s;
            }
        }
        return null;
    }

    static Tree.Statement getLastStatic(
            Tree.InterfaceBody that) {
        List<Tree.Statement> statements = 
                that.getStatements();
        for (int i=statements.size()-1; i>=0; i--) {
            Tree.Statement s = statements.get(i);
            if (s instanceof Tree.Declaration) {
                Tree.Declaration d = (Tree.Declaration) s;
                if (d.getDeclarationModel()
                        .isStatic()) {
                    return s;
                }
            }
        }
        return null;
    }

    static Tree.Declaration getLastConstructor(
            Tree.ClassBody that) {
        List<Tree.Statement> statements = 
                that.getStatements();
        for (int i=statements.size()-1; i>=0; i--) {
            Tree.Statement s = statements.get(i);
            if (s instanceof Tree.Constructor ||
                s instanceof Tree.Enumerated) {
                return (Tree.Declaration) s;
            }
        }
        return null;
    }

    static boolean isExecutableStatement(Unit unit, Tree.Statement s) {
        if (s instanceof Tree.SpecifierStatement) {
            //shortcut refinement statements with => aren't really "executable"
            Tree.SpecifierStatement ss = 
                    (Tree.SpecifierStatement) s;
            Tree.SpecifierExpression se = 
                    ss.getSpecifierExpression();
            return !(ss.getRefinement() &&
                    se instanceof Tree.LazySpecifierExpression);
        }
        else if (s instanceof Tree.ExecutableStatement) {
            return true;
        }
        else {
            if (s instanceof Tree.AttributeDeclaration) {
                Tree.AttributeDeclaration ad = 
                        (Tree.AttributeDeclaration) s;
                Tree.SpecifierOrInitializerExpression sie = 
                        ad.getSpecifierOrInitializerExpression();
        		return !(sie==null ||
        		        sie instanceof Tree.LazySpecifierExpression);
            }
            else if (s instanceof Tree.ObjectDefinition) {
                Tree.ObjectDefinition o = 
                        (Tree.ObjectDefinition) s;
                if (o.getExtendedType()!=null) {
                    Type et = 
                            o.getExtendedType()
                                .getType()
                                .getTypeModel();
        			if (et!=null 
                            && !et.isObject()
                            && !et.isBasic()) {
                        return true;
                    }
                }
                ClassBody body = o.getClassBody();
                if (body!=null) {
                    if (getLastExecutableStatement(body)!=null) {
                        return true;
                    }
                }
                return false;
            }
            else {
                return false;
            }
        }
    }
    
    static String typingMessage(Type type, 
            String problem, Type otherType, 
            Unit unit) {
        Set<TypeDeclaration> declarations = 
                new HashSet<TypeDeclaration>();
        type.collectDeclarations(declarations);
        otherType.collectDeclarations(declarations);
        Set<String> names = new HashSet<String>();
        for (TypeDeclaration td: declarations) {
            names.add(td.getName(unit));
        }
        String unknownTypeError = 
                type.getFirstUnknownTypeError(true);
        String typeName;
        String otherTypeName;
        String expandedTypeName;
        String expandedOtherTypeName;
        if (names.size()<declarations.size()) {
            typeName = type.asQualifiedString();
            otherTypeName = otherType.asQualifiedString();
            expandedTypeName = 
                    type.resolveAliases()
                        .asQualifiedString();
            expandedOtherTypeName = 
                    otherType.resolveAliases()
                        .asQualifiedString();
        }
        else {
            typeName = type.asString(unit);
            otherTypeName = otherType.asString(unit);
            expandedTypeName = 
                    type.resolveAliases()
                        .asString(unit);
            expandedOtherTypeName = 
                    otherType.resolveAliases()
                        .asString(unit);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(": '")
          .append(typeName)
          .append("'");
        if (!typeName.equals(expandedTypeName)) {
            sb.append(" ('")
              .append(expandedTypeName)
              .append("')");
        }
        sb.append(problem);
        sb.append("'")
          .append(otherTypeName)
          .append("'");
        if (!otherTypeName.equals(expandedOtherTypeName)) {
            sb.append(" ('")
              .append(expandedOtherTypeName)
              .append("')");
        }
        if (unknownTypeError!=null) {
            sb.append(": ")
              .append(unknownTypeError);
        }
        return sb.toString();
    }
    
    private static String message(Type type, 
            String problem, Unit unit) {
        String typeName = type.asString(unit);
        String expandedTypeName = 
                type.resolveAliases()
                    .asString(unit);
        StringBuilder sb = new StringBuilder();
        sb.append(": '")
          .append(typeName)
          .append("'");
        if (!typeName.equals(expandedTypeName)) {
            sb.append(" ('")
              .append(expandedTypeName)
              .append("')");
        }
        sb.append(problem);
        return sb.toString();
    }
    
    static boolean checkCallable(Type type, Node node, String message) {
        Unit unit = node.getUnit();
        if (type!=null) {
            type = type.resolveAliases();
        }
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
            return false;
        }
        else if (!unit.isCallableType(type)) {
            if (!hasError(node)) {
                String extra = 
                        message(type, 
                                " is not a subtype of 'Callable'", 
                                unit);
                if (node instanceof Tree.StaticMemberOrTypeExpression) {
                    Tree.StaticMemberOrTypeExpression smte = 
                            (Tree.StaticMemberOrTypeExpression) node;
                    Declaration d = smte.getDeclaration();
                    String name = d.getName();
                    if (d instanceof Interface) {
                        extra = ": '" + name + "' is an interface";
                    }
                    else if (d instanceof TypeAlias) {
                        extra = ": '" + name + "' is a type alias";
                    }
                    else if (d instanceof TypeParameter) {
                        extra = ": '" + name + "' is a type parameter";
                    }
                    else if (d instanceof Value) {
                        extra = ": value '" + name + "' has type '" + 
                                type.asString(unit) + 
                                "' which is not a subtype of 'Callable'";
                    }
                }
                node.addError(message + extra);
            }
            return false;
        }
        else {
            return true;
        }
    }

    static Type checkSupertype(Type type, 
            TypeDeclaration td, Node node, String message) {
        return checkSupertype(type, false, td, node, message);
    }
    
    static Type checkSupertype(Type type, boolean unary, 
            TypeDeclaration td, Node node, String message) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
            return null;
        }
        else {
            Type supertype = type.getSupertype(td);
            if (supertype==null) {
                node.addError(message + 
                        message(type, 
                                " is not a subtype of '" + 
                                        td.getName() + "'", 
                                node.getUnit()));
            }
            else if (!unary &&
                    !supertype.getVarianceOverrides()
                        .isEmpty()) {
                node.addError(message + 
                        message(type, 
                                " does not have a principal instantiation for '" + 
                                        td.getName() + "'",
                                node.getUnit()));
            }
            return supertype;
        }
    }

    static void checkAssignable(Type type, 
            Type supertype, Node node, String message) {
        if (isTypeUnknown(type)) {
        	addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype)) {
            addTypeUnknownError(node, supertype, message);
        }
        else if (!type.isSubtypeOf(supertype)) {
        	node.addError(message + 
        	        notAssignableMessage(type, supertype, node));
        }
    }

    static void checkAssignableWithWarning(Type type, 
            Type supertype, Node node, String message) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype)) {
            addTypeUnknownError(node, supertype, message);
        }
        else if (!type.isSubtypeOf(supertype)) {
        	node.addUnsupportedError(message + 
        	        notAssignableMessage(type, supertype, node));
        }
    }

    static void checkAssignableToOneOf(Type type, 
    		Type supertype1, Type supertype2, 
            Node node, String message, int code) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype1)) {
            addTypeUnknownError(node, supertype1, message);
        }
        else if (isTypeUnknown(supertype2)) {
            addTypeUnknownError(node, supertype2, message);
        }
        else if (!type.isSubtypeOf(supertype1)
                && !type.isSubtypeOf(supertype2)) {
            node.addError(message + 
                    notAssignableMessage(type, supertype1, node), 
                    code);
        }
    }

    static String notAssignableMessage(Type type,
            Type supertype, Node node) {
        Unit unit = node.getUnit();
        String result = 
                typingMessage(type,
                    " is not assignable to ",
                    supertype, unit);
        if (unit.getDefiniteType(type)
                .isSubtypeOf(supertype)) {
            return result + 
                    " (the assigned type contains 'null')";
        }
        else if (unit.isCallableType(type)
                && unit.getCallableReturnType(type)
                    .isSubtypeOf(supertype)) {
            return result + 
                    " (specify arguments to the function reference)";
        }
        else {
            TypeDeclaration typeDec = 
                    type.getDeclaration();
            TypeDeclaration supertypeDec = 
                    supertype.getDeclaration();
            if (typeDec instanceof ClassOrInterface && 
                supertypeDec instanceof ClassOrInterface 
                    && typeDec.isToplevel() 
                    && supertypeDec.isToplevel()) {
                String typeName = typeDec.getName();
                String supertypeName = supertypeDec.getName();
                if (typeName.equals(supertypeName)) {
                    String typePackage = 
                            typeDec.getUnit()
                                .getPackage()
                                .getNameAsString();
                    String supertypePackage = 
                            supertypeDec.getUnit()
                                .getPackage()
                                .getNameAsString();
                    if (typePackage.startsWith("ceylon.") &&
                        supertypePackage.startsWith("java.")) {
                        return result +
                                " (Java '" + 
                                supertypeName + 
                                "' is not the same type as Ceylon '" + 
                                typeName + "')";
                    }
                    if (typePackage.startsWith("java.") &&
                        supertypePackage.startsWith("ceylon.")) {
                        return result +
                                " (Ceylon '" + 
                                supertypeName + 
                                "' is not the same type as Java '" + 
                                typeName + "')";
                    }
                }
            }
            return result;
        }
    }

    static void checkAssignable(Type type, 
            Type supertype, Node node, 
            String message, int code) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype)) {
            addTypeUnknownError(node, supertype, message);
        }
        else if (!type.isSubtypeOf(supertype)) {
            node.addError(message + 
                    notAssignableMessage(type, supertype, node), 
                    code);
        }
    }

    /*static void checkAssignable(Type type, Type supertype, 
            TypeDeclaration td, Node node, String message) {
        if (isTypeUnknown(type) || isTypeUnknown(supertype)) {
            addTypeUnknownError(node, message);
        }
        else if (!type.isSubtypeOf(supertype)) {
            node.addError(message + message(type, " is not assignable to ", supertype, node.getUnit()));
        }
    }*/

    static void checkIsExactly(Type type, 
            Type supertype, Node node, String message) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype)) {
            addTypeUnknownError(node, supertype, message);
        }
        else if (!type.isExactly(supertype)) {
            node.addError(message + 
                    notExactlyMessage(type, supertype, node));
        }
    }

    static void checkIsExactly(Type type, 
            Type supertype, Node node, String message, 
            int code) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype)) {
            addTypeUnknownError(node, supertype, message);
        }
        else if (!type.isExactly(supertype)) {
            node.addError(message + 
                    notExactlyMessage(type, supertype, node), 
                    code);
        }
    }

    static void checkIsExactlyOneOf(Type type, 
    		Type supertype1, Type supertype2, 
            Node node, String message) {
        if (isTypeUnknown(type)) {
            addTypeUnknownError(node, type, message);
        }
        else if (isTypeUnknown(supertype1)) {
            addTypeUnknownError(node, supertype1, message);
        }
        else if (isTypeUnknown(supertype2)) {
            addTypeUnknownError(node, supertype2, message);
        }
        else if (!type.isExactly(supertype1)
                && !type.isExactly(supertype2)) {
            node.addError(message + 
                    notExactlyMessage(type, supertype1, node));
        }
    }

    static String notExactlyMessage(Type type, 
            Type supertype, Node node) {
        return typingMessage(type, 
                " is not exactly ", 
                supertype, 
                node.getUnit());
    }
    
    private static void addTypeUnknownError(Node node, 
            Type type, String message) {
        if (!hasError(node)) {
            node.addError(message + 
                    ": type cannot be determined" +
                    getTypeUnknownError(type));
        }
    }
    
    static String getTypeUnknownError(Type type) {
        if (type == null) {
            return "";
        }
        else {
            String error = type.getFirstUnknownTypeError();
            if (error != null) {
                return ": " + error;
            }
            else {
                return "";
            }
        }
    }

    static String typeDescription(TypeDeclaration td, Unit unit) {
        String name = td.getName();
        if (td instanceof TypeParameter) {
            Declaration container = 
                    (Declaration) td.getContainer();
            return "type parameter '" + name + "' of '" + 
                    container.getName(unit) + "'";
        }
        else {
            return "type '" + name + "'";
        }
    }

    static String typeNamesAsIntersection(
            List<Type> upperBounds, Unit unit) {
        if (upperBounds.isEmpty()) {
            return "Anything";
        }
        StringBuilder sb = new StringBuilder();
        for (Type st: upperBounds) {
            sb.append(st.asString(unit)).append(" & ");
        }
        if (sb.toString().endsWith(" & ")) {
            sb.setLength(sb.length()-3);
        }
        return sb.toString();
    }

    public static boolean isAlwaysSatisfied(Tree.ConditionList cl) {
        if (cl==null) return false;
        for (Tree.Condition c: cl.getConditions()) {
            if (c instanceof Tree.BooleanCondition) {
                Tree.BooleanCondition bc = 
                        (Tree.BooleanCondition) c;
                Tree.Expression ex = bc.getExpression();
                if (ex!=null) {
                    Type type = ex.getTypeModel();
                    if (type!=null && 
                            type.getDeclaration()
                                .isTrueValue()) {
                        continue;
                    }
                }
            }
            return false;
        }
        return true;
    }

    public static boolean isNeverSatisfied(Tree.ConditionList cl) {
        if (cl==null) return false;
        for (Tree.Condition c: cl.getConditions()) {
            if (c instanceof Tree.BooleanCondition) {
                Tree.BooleanCondition bc = 
                        (Tree.BooleanCondition) c;
                Tree.Expression ex = bc.getExpression();
                if (ex!=null) {
                    Type type = ex.getTypeModel();
                    if (type!=null && 
                            type.getDeclaration()
                                .isFalseValue()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public static boolean isAtLeastOne(Tree.ForClause forClause) {
        Tree.ForIterator fi = forClause.getForIterator();
        if (fi!=null) {
            Tree.SpecifierExpression se = 
                    fi.getSpecifierExpression();
            if (se!=null) {
                Tree.Expression e = se.getExpression();
                if (e!=null) {
                    Unit unit = forClause.getUnit();
                    Type at = unit.getAnythingType();
                    Type neit = unit.getNonemptyIterableType(at);
                    Type t = e.getTypeModel();
                    return t!=null && t.isSubtypeOf(neit);
                }
            }
        }
        return false;
    }

    static boolean declaredInPackage(Declaration dec, Unit unit) {
        return dec.getUnit().getPackage()
                .equals(unit.getPackage());
    }

    /**
     * Does not unwrap primary expressions
     */
    public static boolean isIndirectInvocation(
            Tree.InvocationExpression that) {
        return isIndirectInvocation(that, false);
    }

    /**
     * Unwraps primary expressions if you tell it to
     */
    public static boolean isIndirectInvocation(
            Tree.InvocationExpression that, boolean unwrap) {
        return isIndirectInvocation(that.getPrimary(), unwrap);
    }

    private static boolean isIndirectInvocation(
            Tree.Primary primary, boolean unwrap) {
    	Tree.Term term = unwrap ? 
    	        unwrapExpressionUntilTerm(primary) : primary;
        if (term instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = 
                    (Tree.MemberOrTypeExpression) term;
            return isIndirectInvocation(mte);
        }
        else {
           return true;
        }
    }

    private static boolean isIndirectInvocation(
            Tree.MemberOrTypeExpression that) {
        Reference prf = that.getTarget();
        if (prf==null) {
            return true;
        }
        else {
            Declaration d = prf.getDeclaration();
            if (!prf.isFunctional() || 
                    //type parameters are not really callable 
                    //even though they are Functional
                    d instanceof TypeParameter) {
                return true;
            }
            if (that.getStaticMethodReference()) {
                if (d.isStatic() || 
                        isConstructor(d)) {
                    Tree.QualifiedMemberOrTypeExpression qmte = 
                            (Tree.QualifiedMemberOrTypeExpression) 
                                that;
                    return isIndirectInvocation(qmte.getPrimary(), false);
                }
                else {
                    return true;
                }
            }
            return false;
        }
    }
    
    static String message(Declaration dec) {
        String qualifier;
        Scope container = dec.getContainer();
        if (container instanceof Declaration) {
            Declaration cd = (Declaration) container;
            qualifier = " in '" + cd.getName() + "'";
        }
        else {
            qualifier = "";
        }
        return "'" + dec.getName() + "'" + qualifier;
    }
    
    static Node getParameterTypeErrorNode(Tree.Parameter p) {
        if (p instanceof Tree.ParameterDeclaration) {
            Tree.ParameterDeclaration pd = 
                    (Tree.ParameterDeclaration) p;
            return pd.getTypedDeclaration().getType();
        }
        else {
            return p;
        }
    }
    
    public static Node getTypeErrorNode(Node that) {
        if (that instanceof Tree.TypedDeclaration) {
            Tree.TypedDeclaration td = 
                    (Tree.TypedDeclaration) that;
            Tree.Type type = td.getType();
            if (type!=null) {
                return type;
            }
        }
        if (that instanceof Tree.TypedArgument) {
            Tree.TypedArgument ta = 
                    (Tree.TypedArgument) that;
            Tree.Type type = ta.getType();
            if (type!=null) {
                return type;
            }
        }
        if (that instanceof Tree.FunctionArgument) {
            Tree.FunctionArgument fa = 
                    (Tree.FunctionArgument) that;
            Tree.Type type = fa.getType();
            if (type!=null && type.getToken()!=null) {
                return type;
            }
        }
        return that;
    }

    static void checkIsExactlyForInterop(Unit unit, 
            boolean isCeylon,  
            Type parameterType, 
            Type refinedParameterType, 
            Node node, String message) {
        if (isCeylon) {
            // it must be a Ceylon method
            checkIsExactly(parameterType, 
                    refinedParameterType, 
                    node, message, 9200);
        }
        else {
            // we're refining a Java method
            Type refinedDefiniteType = 
                    unit.getDefiniteType(
                            refinedParameterType);
            checkIsExactlyOneOf(parameterType, 
                    refinedParameterType, 
                    refinedDefiniteType, 
            		node, message);
        }
    }

    public static Type getTupleType(
            List<Tree.PositionalArgument> es, 
            Unit unit, 
            boolean requireSequential) {
        Type result = unit.getEmptyType();
        Type ut = unit.getNothingType();
        Class td = unit.getTupleDeclaration();
        Interface id = unit.getIterableDeclaration();
        for (int i=es.size()-1; i>=0; i--) {
            Tree.PositionalArgument a = es.get(i);
            Type t = a.getTypeModel();
            if (t!=null) {
                Type et = t; //unit.denotableType(t);
                if (a instanceof Tree.SpreadArgument) {
                    /*if (requireSequential) { 
                        checkSpreadArgumentSequential((Tree.SpreadArgument) a, et);
                    }*/
                    ut = unit.getIteratedType(et);
                    if (ut != null) {
                        result = spreadType(et, unit, 
                                requireSequential);
                    } else {
                        if (unit.isJavaIterableType(et)) {
                            ut = unit.getJavaIteratedType(et);
                            result = unit.getIterableType(ut);
                        } else if (unit.isJavaArrayType(et)) {
                            ut = unit.getJavaArrayElementType(et);//unit.getJavaObjectArrayDeclaration().appliedType(null, Collections.singletonList());
                            result = unit.getIterableType(ut);
                        } 
                    }
                    
                }
                else if (a instanceof Tree.Comprehension) {
                    ut = et;
                    Tree.Comprehension c = 
                            (Tree.Comprehension) a;
                    Tree.InitialComprehensionClause icc = 
                            c.getInitialComprehensionClause();
                    result = icc.getPossiblyEmpty() ? 
                            unit.getSequentialType(et) : 
                            unit.getSequenceType(et);
                    if (!requireSequential) {
                        Type it = 
                                appliedType(id, et, 
                                        icc.getFirstTypeModel());
                        result = intersectionType(result, it, unit);
                    }
                }
                else {
                    ut = unionType(ut, et, unit);
                    result = appliedType(td, ut, et, result);
                }
            }
        }
        return result;
    }
    
    static Type spreadType(Type et, Unit unit,
            boolean requireSequential) {
        if (et==null) return null;
        if (requireSequential) {
            if (unit.isSequentialType(et)) {
                //if (et.isTypeParameter()) {
                    return et;
                /*}
                else {
                    // if it's already a subtype of Sequential, erase 
                    // out extraneous information, like that it is a
                    // String, just keeping information about what
                    // kind of tuple it is
                    List<Type> elementTypes = unit.getTupleElementTypes(et);
                    boolean variadic = unit.isTupleLengthUnbounded(et);
                    boolean atLeastOne = unit.isTupleVariantAtLeastOne(et);
                    int minimumLength = unit.getTupleMinimumLength(et);
                    if (variadic) {
                        Type spt = elementTypes.get(elementTypes.size()-1);
                        elementTypes.set(elementTypes.size()-1, unit.getIteratedType(spt));
                    }
                    return unit.getTupleType(elementTypes, variadic, 
                            atLeastOne, minimumLength);
                }*/
            }
            else {
                // transform any Iterable into a Sequence without
                // losing the information that it is nonempty, in
                // the case that we know that for sure
                Type it = unit.getIteratedType(et);
                Type st = 
                        unit.isNonemptyIterableType(et) ?
                                unit.getSequenceType(it) :
                                unit.getSequentialType(it);
                // unless this is a tuple constructor, remember
                // the original Iterable type arguments, to
                // account for the possibility that the argument
                // to Absent is a type parameter
                //return intersectionType(et.getSupertype(unit.getIterableDeclaration()), st, unit);
                // for now, just return the sequential type:
                return st;
            }
        }
        else {
            return et;
        }
    }

    static boolean setTypeConstructor(Tree.Type t,
            TypeParameter typeParam) {
        Type pt = t.getTypeModel();
        if (pt == null) {
            return false;
        }
        else {
            /*if (t instanceof Tree.IntersectionType) {
                Tree.IntersectionType it = 
                        (Tree.IntersectionType) t;
                for (Tree.StaticType st: it.getStaticTypes()) {
                    if (setTypeConstructor(st,typeParam)) {
                        pt.setTypeConstructor(true);
                        pt.setTypeConstructorParameter(typeParam);
                    }
                }
            }
            else if (t instanceof Tree.UnionType) {
                Tree.UnionType it = 
                        (Tree.UnionType) t;
                for (Tree.StaticType st: it.getStaticTypes()) {
                    if (setTypeConstructor(st,typeParam)) {
                        pt.setTypeConstructor(true);
                        pt.setTypeConstructorParameter(typeParam);
                    }
                }
            }
            else*/ 
            if (t instanceof Tree.SimpleType) {
                Tree.SimpleType s = 
                        (Tree.SimpleType) t;
                if (s.getTypeArgumentList()==null) {
                    if (typeParam!=null || 
                            isGeneric(s.getDeclarationModel())) {
                        pt.setTypeConstructor(true);
                        pt.setTypeConstructorParameter(typeParam);
                    }
                }
            }
            return pt.isTypeConstructor();
        }
    }

    static boolean isGeneric(Declaration member) {
        if (member instanceof Generic) {
            Generic g = (Generic) member;
            return !g.getTypeParameters().isEmpty();
        }
        else {
            return false;
        }
    }

    static boolean inSameModule(TypeDeclaration etd, Unit unit) {
        return etd.getUnit().getPackage().getModule()
        		.equals(unit.getPackage().getModule());
    }

    static void checkCasesDisjoint(Type type, Type other,
            Node node) {
        if (!isTypeUnknown(type) && !isTypeUnknown(other)) {
            Unit unit = node.getUnit();
            Type it = 
                    intersectionType(type.resolveAliases(), 
                            other.resolveAliases(), unit);
            if (!it.isNothing()) {
                node.addError("cases are not disjoint: '" + 
                        type.asString(unit) + "' and '" + 
                        other.asString(unit) + "'");
            }
        }
    }

    static Parameter getMatchingParameter(ParameterList pl, 
            Tree.NamedArgument na, 
            Set<Parameter> foundParameters) {
        Tree.Identifier id = na.getIdentifier();
        if (id==null) {
            for (Parameter p: pl.getParameters()) {
                if (!foundParameters.contains(p)) {
                    return p;
                }
            }
        }
        else {
            String name = name(id);
            for (Parameter p: pl.getParameters()) {
                if (p.getName()!=null &&
                        p.getName().equals(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    static Parameter getUnspecifiedParameter(Reference pr,
            ParameterList pl, 
            Set<Parameter> foundParameters) {
        for (Parameter p: pl.getParameters()) {
            Type t = pr==null ? 
                    p.getType() : 
                    pr.getTypedParameter(p)
                        .getFullType();
            if (t!=null) {
                t = t.resolveAliases();
                if (!foundParameters.contains(p) &&
                        p.getDeclaration().getUnit()
                            .isIterableParameterType(t)) {
                    return p;
                }
            }
        }
        return null;
    }

    static boolean involvesTypeParams(Declaration dec, 
            Type type) {
        if (isGeneric(dec)) {
            Generic g = (Generic) dec;
            return type.involvesTypeParameters(g);
        }
        else {
            return false;
        }
    }

    static TypeDeclaration unwrapAliasedTypeConstructor(
            TypeDeclaration dec) {
        TypeDeclaration d = dec;
        while (!isGeneric(d) && d.isAlias()) {
            Type et = d.getExtendedType();
            if (et==null) break;
            et = et.resolveAliases();
            d = et.getDeclaration();
            if (et.isTypeConstructor() && isGeneric(d)) {
                return d;
            }
        }
        return dec;
    }

    static Type unwrapAliasedTypeConstructor(Type type) {
        TypeDeclaration d = type.getDeclaration();
        while (!isGeneric(d) && d.isAlias()) {
            Type et = d.getExtendedType();
            if (et==null) break;
            d = et.getDeclaration();
            et = et.resolveAliases();
            if (et.isTypeConstructor() && isGeneric(d)) {
                return et;
            }
        }
        return type;
    }

    static Package importedPackage(Tree.ImportPath path, Unit unit) {
        if (path!=null && 
                !path.getIdentifiers().isEmpty()) {
            String nameToImport = 
                    formatPath(path.getIdentifiers());
            Module module = 
                    path.getUnit()
                        .getPackage()
                        .getModule();
            Package pkg = module.getPackage(nameToImport);
            if (pkg != null) {
                Module pkgMod = pkg.getModule();
                if (pkgMod.equals(module)) {
                    return pkg;
                }
                if (!pkg.isShared()) {
                    path.addError("imported package is not shared: '" + 
                            nameToImport + "' is not annotated 'shared' in '" +
                            pkgMod.getNameAsString() + "'", 402);
                }
//                if (module.isDefault() && 
//                        !pkg.getModule().isDefault() &&
//                        !pkg.getModule().getNameAsString()
//                            .equals(Module.LANGUAGE_MODULE_NAME)) {
//                    path.addError("package belongs to a module and may not be imported by default module: " +
//                            nameToImport);
//                }
                //check that the package really does belong to
                //an imported module, to work around bug where
                //default package thinks it can see stuff in
                //all modules in the same source dir
                Set<Module> visited = new HashSet<Module>();
                for (ModuleImport mi: module.getImports()) {
                    if (findModuleInTransitiveImports(
                            mi.getModule(), 
                            pkgMod, 
                            visited)) {
                        return pkg; 
                    }
                }
            }
            else {
                for (ModuleImport mi: module.getImports()) {
                    if (mi.isNative()) {
                        String name = 
                                mi.getModule()
                                    .getNameAsString();
                        if (!isForBackend(mi.getNativeBackends(), 
                                          path.getUnit()
                                              .getSupportedBackends()) &&
                                (nameToImport.equals(name) ||
                                 nameToImport.startsWith(name + "."))) {
                            return null;
                        }
                        if (!isForBackend(Backend.Java.asSet(), 
                                          path.getUnit()
                                              .getSupportedBackends()) &&
                                unit.isJdkPackage(nameToImport)) {
                            return null;
                        }
                    }
                }
            }
            String help = module.isDefaultModule() ? 
                    " (define a module and add module import to its module descriptor)" : 
                    " (add module import to module descriptor of '" +
                        module.getNameAsString() + "')";
            path.addError("package not found in imported modules: '" + 
                    nameToImport + "'" + help, 7000);
        }
        return null;
    }
    
    static Module importedModule(Tree.ImportPath path) {
        if (path!=null && 
                !path.getIdentifiers().isEmpty()) {
            String nameToImport = 
                    formatPath(path.getIdentifiers());
            Module module = 
                    path.getUnit()
                        .getPackage()
                        .getModule();
            Package pkg = module.getPackage(nameToImport);
            if (pkg != null) {
                Module mod = pkg.getModule();
                String moduleName = mod.getNameAsString();
                if (!pkg.getNameAsString().equals(moduleName)) {
                    path.addError("not a module: '" + 
                            nameToImport + "' is a package belonging to '" +
                            moduleName + "'");
                    return null;
                }
                if (mod.equals(module)) {
                    return mod;
                }
                //check that the package really does belong to
                //an imported module, to work around bug where
                //default package thinks it can see stuff in
                //all modules in the same source dir
                Set<Module> visited = new HashSet<Module>();
                for (ModuleImport mi: module.getImports()) {
                    Module m = mi.getModule();
                    if (findModuleInTransitiveImports(m, mod, 
                            visited)) {
                        return mod; 
                    }
                }
            }
            path.addError("module not found in imported modules: '" + 
                    nameToImport + "'", 7000);
        }
        return null;
    }

    private static boolean findModuleInTransitiveImports(
            Module moduleToVisit, Module moduleToFind, 
            Set<Module> visited) {
        if (!visited.add(moduleToVisit)) {
            return false;
        }
        else if (moduleToVisit.equals(moduleToFind)) {
            return true;
        }
        else {
            for (ModuleImport imp: moduleToVisit.getImports()) {
                // skip non-exported modules
                if (imp.isExport() &&
                        findModuleInTransitiveImports(
                                imp.getModule(), 
                                moduleToFind, visited)) {
                    return true;
                }
            }
            return false;
        }
    }

    static boolean isVeryAbstractClass(
            Tree.ClassDefinition that,
            Unit unit) {
        String pname = 
                unit.getPackage()
                    .getQualifiedNameString();
        String name = name(that.getIdentifier());
        return Module.LANGUAGE_MODULE_NAME.equals(pname) &&
                ("Anything".equalsIgnoreCase(name) ||
                "Object".equalsIgnoreCase(name) ||
                "Basic".equalsIgnoreCase(name) ||
                "Null".equalsIgnoreCase(name));
    }

    static boolean hasUncheckedNullType(Reference member) {
        Declaration dec = member.getDeclaration();
        return dec instanceof TypedDeclaration && 
                ((TypedDeclaration) dec)
                    .hasUncheckedNullType();
    }

    static String correctionMessage(String name, 
            Scope scope, Unit unit, Cancellable cancellable) {
        DeclarationWithProximity correction = 
                correct(scope, unit, name, cancellable);
        if (correction!=null) {
            if (correction.getName().equals(name)) {
                if (correction.isUnimported()) {
                    return " might be misspelled or is not imported (did you mean to import it from '" 
                        + correction.packageName() + "'?)";
                }
                else {
                    return "";
                }
            }
            else {
                if (correction.isUnimported()) {
                    return " might be misspelled or is not imported (did you mean '" 
                        + correction.realName(unit) + "' from '" 
                        + correction.packageName() + "'?)";
                }
                else {
                    return " might be misspelled or is not imported (did you mean '" 
                        + correction.realName(unit) + "'?)";
                }
            }
        }
        else {
            return " might be misspelled or is not imported";
        }
    }

    static String memberCorrectionMessage(String name, 
            TypeDeclaration d, Scope scope, Unit unit, 
            Cancellable cancellable) {
        DeclarationWithProximity correction =
                correct(d, scope, unit, name, cancellable);
        if (correction==null) {
            return " might be misspelled";
        }
        else {
            return " might be misspelled (did you mean '" 
                + correction.realName(unit) + "'?)";
        }
    }

    static String importCorrectionMessage(String name, 
            Package scope, Unit unit, 
            Cancellable cancellable) {
        DeclarationWithProximity correction =
                correct(scope, name, cancellable);
        if (correction==null) {
            return " might be misspelled or does not belong to this package";
        }
        else {
            return " might be misspelled or does not belong to this package (did you mean '" 
                + correction.realName(unit) + "'?)";
        }
    }

}
