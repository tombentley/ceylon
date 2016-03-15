/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.codegen;

import java.util.Iterator;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.langtools.tools.javac.code.Flags;
import com.redhat.ceylon.langtools.tools.javac.tree.JCTree.JCAnnotation;
import com.redhat.ceylon.langtools.tools.javac.tree.JCTree.JCExpression;
import com.redhat.ceylon.langtools.tools.javac.tree.JCTree.JCVariableDecl;
import com.redhat.ceylon.langtools.tools.javac.util.List;
import com.redhat.ceylon.langtools.tools.javac.util.ListBuffer;
import com.redhat.ceylon.langtools.tools.javac.util.Name;
import com.redhat.ceylon.model.typechecker.model.Annotation;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ParameterList;

public class ParameterDefinitionBuilder {

    private final AbstractTransformer gen;
    
    private long modifiers;
    
    private JCExpression type;
    
    private List<JCAnnotation> typeAnnos;

    private boolean sequenced;

    private boolean defaulted;
    
    private final String name;
    
    private String aliasedName;
    
    private int annotationFlags = Annotations.MODEL_AND_USER;
    
    private boolean built = false;

    private ListBuffer<JCAnnotation> userAnnotations;
    private ListBuffer<JCAnnotation> modelAnnotations;

    private String functionalParameterName;

    private FunctionOrValue boxedVariable;

    private Node at;

    private ParameterDefinitionBuilder(AbstractTransformer gen, String name) {
        this.gen = gen;
        this.name = name;
    }
    
    /**
     * Creates a builder for a parameter lacking a Parameter model, but which 
     * doesn't count as an 
     * {@linkplain #implicitParameter(AbstractTransformer, String) implicit} 
     * parameter.
     */
    static ParameterDefinitionBuilder systemParameter(AbstractTransformer gen, String name) {
        return new ParameterDefinitionBuilder(gen, name);
    }
    
    /** 
     * Creates a builder for an implicit parameter 
     * (a reified type parameter, or a $this parameter for a companion class)
     */
    static ParameterDefinitionBuilder implicitParameter(AbstractTransformer gen, String name) {
        return new ParameterDefinitionBuilder(gen, name);
    }
    
    /** 
     * Creates a builder for a explicit parameter (anything for which there's 
     * a Parameter model).
     */
    static ParameterDefinitionBuilder explicitParameter(AbstractTransformer gen, Parameter parameter) {
        ParameterDefinitionBuilder pdb = new ParameterDefinitionBuilder(gen, parameter.getName());
        if (isBoxedVariableParameter(parameter)) {
            pdb.boxedVariable = parameter.getModel();
        }
        if (parameter.getModel() instanceof Function) {
            pdb.functionalParameterName = functionalName((Function)parameter.getModel());
        }
        return pdb;
    }

    private static String functionalName(Function model) {
        StringBuilder sb = new StringBuilder();
        functionalName(sb, model);
        return sb.toString();
    }
    
    private static void functionalName(StringBuilder sb, Function model) {
        if (model.isDeclaredVoid()) {
            sb.append('!');
        }
        for (ParameterList pl : model.getParameterLists()) {
            functionalParameters(sb, pl);
        }
    }

    static void functionalParameters(StringBuilder sb, ParameterList pl) {
        sb.append('(');
        Iterator<Parameter> parameters = pl.getParameters().iterator();
        while (parameters.hasNext()) {
            Parameter p = parameters.next();
            FunctionOrValue pm = p.getModel();
            sb.append(pm.getName());
            if(p.isSequenced()) {
                if (p.isAtLeastOne()) {
                    sb.append('+');
                } else {
                    sb.append('*');
                }
            }
            if (pm instanceof Function) {
                functionalName(sb, (Function)pm);
            }
            if (parameters.hasNext()) {
                sb.append(',');
            }
        }
        sb.append(')');
    }

    static boolean isBoxedVariableParameter(Parameter parameter) {
        return parameter.getModel().isCaptured()
                && parameter.getModel().isVariable()
                && (!parameter.getModel().isClassOrInterfaceMember() || Decl.isLocalToInitializer(parameter.getModel()))
                && !parameter.isHidden();
    }

    public ParameterDefinitionBuilder modifiers(long mods) {
        this.modifiers |= mods;
        return this;
    }
    
    public ParameterDefinitionBuilder userAnnotations(List<JCAnnotation> annos) {
        if (annos != null) {
            if (this.userAnnotations == null) {
                this.userAnnotations = ListBuffer.lb();
            }
            this.userAnnotations.appendList(annos);
        }
        return this;
    }
    
    public ParameterDefinitionBuilder modelAnnotations(java.util.List<Annotation> annos) {
        if (annos != null) {
            if (this.modelAnnotations == null) {
                this.modelAnnotations = ListBuffer.lb();
            }
            this.modelAnnotations.appendList(gen.makeAtAnnotations(annos));
        }
        return this;
    }
    
    public ParameterDefinitionBuilder type(JCExpression type, List<JCAnnotation> typeAnnos) {
        this.type = type;
        this.typeAnnos = typeAnnos;
        return this;
    }
    
    public ParameterDefinitionBuilder sequenced(boolean sequenced) {
        this.sequenced = sequenced;
        return this;
    }
    
    public ParameterDefinitionBuilder aliasName(String aliasedName) {
        this.aliasedName = aliasedName;
        return this;
    }
    
    /** 
     * Adds the {@code @Ignore} model annotation instead of adding the 
     * actual model annotations.
     */
    public ParameterDefinitionBuilder ignored() {
        this.annotationFlags = Annotations.ignore(this.annotationFlags);
        return this;
    }
    
    public ParameterDefinitionBuilder defaulted(boolean defaulted) {
        this.defaulted = defaulted;
        return this;
    }
    
    /**
     * Prevents adding the user or model annotations. Does not affect 
     * whether {@code @Ignore} is added.
     */
    public ParameterDefinitionBuilder noUserOrModelAnnotations() {
        this.annotationFlags = Annotations.noUserOrModel(annotationFlags);
        return this;
    }
    
    public ParameterDefinitionBuilder noModelAnnotations() {
        this.annotationFlags = Annotations.noModel(annotationFlags);
        return this;
    }
    
    public JCVariableDecl build() {
        if (built) {
            throw new BugException("already built");
        }
        built = true;
        ListBuffer<JCAnnotation> annots = ListBuffer.lb();
        if (Annotations.includeModel(annotationFlags)) {
            annots.appendList(gen.makeAtName(name));
            if (functionalParameterName != null) {
                annots.appendList(gen.makeAtFunctionalParameter(functionalParameterName));
            }
            if (sequenced) {
                annots.appendList(gen.makeAtSequenced());
            }
            if (defaulted) {
                annots.appendList(gen.makeAtDefaulted());
            }
            if (typeAnnos != null) {
                annots.appendList(typeAnnos);
            }
        }
        if (Annotations.includeUser(annotationFlags)
                && userAnnotations != null) {
            annots.appendList(userAnnotations.toList());
        }
        if (Annotations.includeModel(annotationFlags)) {
            if (modelAnnotations != null) {
                annots.appendList(modelAnnotations.toList());
            }
        }
        
        if(Annotations.includeIgnore(annotationFlags)){
            annots = annots.appendList(gen.makeAtIgnore());
        }
        Name name = gen.names().fromString(Naming.quoteParameterName(getJavaParameterName()));
        return gen.make().VarDef(gen.make().Modifiers(modifiers | Flags.PARAMETER, annots.toList()), 
                name, type, null);   
    }

    private String getJavaParameterName() {
        return aliasedName != null ? aliasedName : this.name;
    }
    
    public boolean requiresBoxedVariableDecl() {
        return boxedVariable != null;
    }
    
    public JCVariableDecl buildBoxedVariableDecl() {
        return gen.makeVariableBoxDecl(
                gen.naming.makeUnquotedIdent(getJavaParameterName()), 
                boxedVariable);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Flags.toString(modifiers)).append(' ');
        sb.append(type).append(' ');
        sb.append(aliasedName != null ? aliasedName : name);
        return sb.toString();
    }

    public void at(Node node) {
        this.at = node;
    }
    
}
