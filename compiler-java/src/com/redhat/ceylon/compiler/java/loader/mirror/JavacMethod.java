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
package com.redhat.ceylon.compiler.java.loader.mirror;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.redhat.ceylon.javax.lang.model.element.ElementKind;
import com.redhat.ceylon.javax.lang.model.type.TypeKind;
import com.redhat.ceylon.langtools.tools.javac.code.Flags;
import com.redhat.ceylon.langtools.tools.javac.code.Type;
import com.redhat.ceylon.langtools.tools.javac.code.Symbol.MethodSymbol;
import com.redhat.ceylon.langtools.tools.javac.code.Symbol.VarSymbol;
import com.redhat.ceylon.model.loader.mirror.AnnotationMirror;
import com.redhat.ceylon.model.loader.mirror.ClassMirror;
import com.redhat.ceylon.model.loader.mirror.MethodMirror;
import com.redhat.ceylon.model.loader.mirror.TypeMirror;
import com.redhat.ceylon.model.loader.mirror.TypeParameterMirror;
import com.redhat.ceylon.model.loader.mirror.VariableMirror;

public class JavacMethod implements MethodMirror {

    public MethodSymbol methodSymbol;
    
    private TypeMirror returnType;
    private List<VariableMirror> parameters;
    private Map<String, AnnotationMirror> annotations;
    private List<TypeParameterMirror> typeParams;

    private ClassMirror enclosingClass;

    public JavacMethod(ClassMirror enclosingClass, MethodSymbol sym) {
        this.methodSymbol = sym;
        this.enclosingClass = enclosingClass;
    }

    @Override
    public AnnotationMirror getAnnotation(String type) {
        if (annotations == null) {
            annotations = JavacUtil.getAnnotations(methodSymbol);
        }
        return annotations.get(type);
    }
    
    public String toString() {
        return getClass().getSimpleName() + " of " + methodSymbol;
    }

    @Override
    public String getName() {
        return methodSymbol.name.toString();
    }

    @Override
    public boolean isStatic() {
        return methodSymbol.isStatic();
    }

    @Override
    public boolean isPublic() {
        return (methodSymbol.flags() & Flags.PUBLIC) != 0;
    }
    
    @Override
    public boolean isProtected() {
        return (methodSymbol.flags() & Flags.PROTECTED) != 0;
    }
    
    @Override
    public boolean isDefaultAccess() {
        return (methodSymbol.flags() & (Flags.PROTECTED | Flags.PUBLIC | Flags.PRIVATE)) == 0;
    }

    @Override
    public boolean isConstructor() {
        return methodSymbol.isConstructor();
    }

    @Override
    public boolean isStaticInit() {
        return methodSymbol.getKind() == ElementKind.STATIC_INIT;
    }

    @Override
    public boolean isVariadic() {
        return methodSymbol.isVarArgs();
    }

    @Override
    public List<VariableMirror> getParameters() {
        if (parameters == null) {
            com.redhat.ceylon.langtools.tools.javac.util.List<VarSymbol> params = methodSymbol.getParameters();
            List<VariableMirror> ret = new ArrayList<VariableMirror>(params.size());
            for(VarSymbol parameter : params)
                ret.add(new JavacVariable(parameter));
            parameters = Collections.unmodifiableList(ret);
        }
        return parameters;
    }

    @Override
    public boolean isAbstract() {
        return (methodSymbol.flags() & Flags.ABSTRACT) != 0;
    }
    
    @Override
    public boolean isDefaultMethod() {
        return (methodSymbol.flags() & Flags.DEFAULT) != 0;
    }

    @Override
    public boolean isFinal() {
        return (methodSymbol.flags() & Flags.FINAL) != 0;
    }

    @Override
    public TypeMirror getReturnType() {
        if(returnType == null){
            Type retType = methodSymbol.getReturnType();
            if (retType != null) {
                returnType = new JavacType(retType);
            }
        }
        return returnType;
    }
    
    @Override
    public boolean isDeclaredVoid() {
        final Type retType = this.methodSymbol.getReturnType();
        return (retType).getKind() == TypeKind.VOID;
    }

    @Override
    public List<TypeParameterMirror> getTypeParameters() {
        if (typeParams == null) {
            typeParams = Collections.unmodifiableList(JavacUtil.getTypeParameters(methodSymbol));
        }
        return typeParams;
    }

    @Override
    public boolean isDefault() {
        return methodSymbol.getDefaultValue() != null;
    }

    @Override
    public ClassMirror getEnclosingClass() {
        return enclosingClass;
    }
    
}
