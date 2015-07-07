package com.redhat.ceylon.compiler.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.compiler.js.GenerateJsVisitor.SuperVisitor;
import com.redhat.ceylon.compiler.js.util.TypeUtils;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.ModelUtil;
import com.redhat.ceylon.model.typechecker.model.Type;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypeParameter;
import com.redhat.ceylon.model.typechecker.model.Value;

public class Singletons {
    
    static void defineObject(final Node that, final Value d, final List<Type> sats,
            final Tree.SimpleType superType, final Tree.InvocationExpression superCall,
            final Tree.Body body, final Tree.AnnotationList annots, final GenerateJsVisitor gen) {
        final boolean addToPrototype = gen.opts.isOptimize() && d != null && d.isClassOrInterfaceMember();
        final boolean isObjExpr = that instanceof Tree.ObjectExpression;
        final TypeDeclaration _td = isObjExpr ? ((Tree.ObjectExpression)that).getAnonymousClass() : d.getTypeDeclaration();
        final Class c = (Class)(_td instanceof Constructor ? ((Constructor)_td).getContainer() : _td);
        final String className = gen.getNames().name(c);
        final String objectName = gen.getNames().name(d);
        final String selfName = gen.getNames().self(c);
        final boolean genClass;
        final boolean genObj;

        if (d != null && d.isNative()) {
            if (d.isNativeHeader()) {
                genClass = true;
                Value nv = (Value)ModelUtil.getNativeDeclaration(d, Backend.JavaScript);
                genObj = nv == null;
                if (nv != null) {
                    //Force the names of unshared members of the native type to be the same as for its
                    //header's counterparts
                    for (Declaration nd : c.getMembers()) {
                        if (!nd.isShared()) {
                            gen.getNames().forceName(nv.getTypeDeclaration().getMember(
                                    nd.getName(), null, false), gen.getNames().name(nd));
                        }
                    }
                }
            } else {
                genObj = TypeUtils.isForBackend(d);
                genClass = genObj;
            }
        } else {
            genClass = true;
            genObj = true;
        }

        Map<TypeParameter, Type> targs=new HashMap<TypeParameter, Type>();
        if (sats != null) {
            for (Type st : sats) {
                Map<TypeParameter, Type> stargs = st.getTypeArguments();
                if (stargs != null && !stargs.isEmpty()) {
                    targs.putAll(stargs);
                }
            }
        }
        if (genClass) {
            gen.out(GenerateJsVisitor.function, className, targs.isEmpty()?"()":"($$targs$$)");
            gen.beginBlock();
            if (isObjExpr) {
                gen.out("var ", selfName, "=new ", className, ".$$;");
                final ClassOrInterface coi = ModelUtil.getContainingClassOrInterface(c.getContainer());
                if (coi != null) {
                    gen.out(selfName, ".outer$=", gen.getNames().self(coi));
                    gen.endLine(true);
                }
            } else {
                if (c.isMember()) {
                    gen.initSelf(that);
                }
                gen.instantiateSelf(c);
                gen.referenceOuter(c);
            }

            //TODO should we generate all this code for native headers?
            //Really we should merge the body of the header with that of the impl
            //It's the only way to make this shit work in lexical scope mode
            final List<Declaration> superDecs = new ArrayList<Declaration>();
            if (!gen.opts.isOptimize()) {
                new SuperVisitor(superDecs).visit(body);
            }
            if (!targs.isEmpty()) {
                gen.out(selfName, ".$$targs$$=$$targs$$");
                gen.endLine(true);
            }
            TypeGenerator.callSupertypes(sats, superType, c, that, superDecs, superCall,
                    superType == null ? null : ((Class) c.getExtendedType().getDeclaration()).getParameterList(), gen);
            
            body.visit(gen);
            gen.out("return ", selfName, ";");
            gen.endBlock();
            gen.out(";", className, ".$crtmm$=");
            TypeUtils.encodeForRuntime(that, c, gen);
            gen.endLine(true);
            TypeGenerator.initializeType(that, gen);
        }
        if (!genObj) {
            return;
        }
        final String objvar = (addToPrototype ? "this.":"")+gen.getNames().createTempVariable();

        if (d != null && !addToPrototype) {
            gen.out("var ", objvar);
            //If it's a property, create the object here
            if (AttributeGenerator.defineAsProperty(d)) {
                gen.out("=", className, "(");
                if (!targs.isEmpty()) {
                    TypeUtils.printTypeArguments(that, targs, gen, false, null);
                }
                gen.out(")");
            }
            gen.endLine(true);
        }

        if (d != null && AttributeGenerator.defineAsProperty(d)) {
            gen.out(gen.getClAlias(), "atr$(");
            gen.outerSelf(d);
            gen.out(",'", objectName, "',function(){return ");
            if (addToPrototype) {
                gen.out("this.", gen.getNames().privateName(d));
            } else {
                gen.out(objvar);
            }
            gen.out(";},undefined,");
            TypeUtils.encodeForRuntime(d, annots, gen);
            gen.out(")");
            gen.endLine(true);
        } else if (d != null) {
            final String objectGetterName = gen.getNames().getter(d, false);
            gen.out(GenerateJsVisitor.function, objectGetterName, "()");
            gen.beginBlock();
            //Create the object lazily
            final String oname = gen.getNames().objectName(c);
            gen.out("if(", objvar, "===", gen.getClAlias(), "INIT$)");
            gen.generateThrow(gen.getClAlias()+"InitializationError",
                    "Cyclic initialization trying to read the value of '" +
                    d.getName() + "' before it was set", that);
            gen.endLine(true);
            gen.out("if(", objvar, "===undefined){", objvar, "=", gen.getClAlias(), "INIT$;",
                    objvar, "=$init$", oname);
            if (!oname.endsWith("()")) {
                gen.out("()");
            }
            gen.out("(");
            if (!targs.isEmpty()) {
                TypeUtils.printTypeArguments(that, targs, gen, false, null);
            }
            gen.out(");", objvar, ".$crtmm$=", objectGetterName, ".$crtmm$;}");
            gen.endLine();
            gen.out("return ", objvar, ";");
            gen.endBlockNewLine();            
            
            if (addToPrototype || d.isShared()) {
                gen.outerSelf(d);
                gen.out(".", objectGetterName, "=", objectGetterName);
                gen.endLine(true);
            }
            if (!d.isToplevel()) {
                if(gen.outerSelf(d))gen.out(".");
            }
            gen.out(objectGetterName, ".$crtmm$=");
            TypeUtils.encodeForRuntime(d, annots, gen);
            gen.endLine(true);
            gen.out(gen.getNames().getter(c, true), "=", objectGetterName);
            gen.endLine(true);
            if (d.isToplevel()) {
                final String objectGetterNameMM = gen.getNames().getter(d, true);
                gen.out("ex$.", objectGetterNameMM, "=", objectGetterNameMM);
                gen.endLine(true);
            }
        } else if (isObjExpr) {
            gen.out("return ", className, "();");
        }
    }

    static void objectDefinition(final Tree.ObjectDefinition that, final GenerateJsVisitor gen) {
        final Tree.SatisfiedTypes sts = that.getSatisfiedTypes();
        final Tree.ExtendedType et = that.getExtendedType();
        defineObject(that, that.getDeclarationModel(),
                sts == null ? null : TypeUtils.getTypes(sts.getTypes()),
                et == null ? null : et.getType(), et == null ? null : et.getInvocationExpression(),
                that.getClassBody(), that.getAnnotationList(), gen);
        //Objects defined inside methods need their init sections are exec'd
        if (!that.getDeclarationModel().isToplevel() && !that.getDeclarationModel().isClassOrInterfaceMember()) {
            gen.out("/*ESTO*/");
            gen.out(gen.getNames().objectName(that.getDeclarationModel()), "();");
        }
    }

    static void valueConstructor(final Tree.ClassDefinition cdef,
            final Tree.Enumerated that, final GenerateJsVisitor gen) {
        final Value d = that.getDeclarationModel();
        final Constructor c = that.getEnumerated();
        final Tree.DelegatedConstructor dc = that.getDelegatedConstructor();
        final TypeDeclaration td = (TypeDeclaration)c.getContainer();
        final String objvar = gen.getNames().self(td);
        final String typevar = gen.getNames().name(td);
        final String singvar = gen.getNames().name(d);
        gen.out("var ", objvar, ";function ", typevar, "_", singvar, "(){if(",
                objvar, "===undefined){");
        if (dc==null) {
            gen.out("$init$", typevar, "();");
        }
        gen.out(objvar, "=");
        if (dc == null) {
            gen.out("new ", typevar, ".$$;");
            if (td.isClassOrInterfaceMember()) {
                gen.out(objvar, ".outer$=this;");
            }
        } else {
            dc.getInvocationExpression().visit(gen);
            gen.endLine(true);
        }
        List<? extends Tree.Statement> stmts = Constructors.classStatementsBetweenConstructors(cdef, null, that);
        if (!stmts.isEmpty()) {
            gen.visitStatements(stmts);
        }
        stmts = Constructors.classStatementsAfterConstructor(cdef, that);
        if (!stmts.isEmpty()) {
            gen.visitStatements(stmts);
        }
        gen.out("}return ", objvar, ";};", typevar, "_", singvar, ".$crtmm$=");
        TypeUtils.encodeForRuntime(d, that.getAnnotationList(), gen);
        gen.out(";");
        if (td.isClassOrInterfaceMember()) {
            gen.outerSelf(td);
            gen.out(".", typevar, "_", singvar, "=", typevar, "_", singvar, ";");
        }
    }

}