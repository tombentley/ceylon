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

import java.util.HashSet;
import java.util.Set;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.langtools.tools.javac.jvm.Target;
import com.redhat.ceylon.langtools.tools.javac.util.Context;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Functional;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.Function;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ParameterList;
import com.redhat.ceylon.model.typechecker.model.Setter;
import com.redhat.ceylon.model.typechecker.model.TypeParameter;
import com.redhat.ceylon.model.typechecker.model.Value;

/**
 * Visits every local interface and computes their Java companion class name.
 * Also visits every interface and calculates if they need a companion class. 
 * 
 * @author Stéphane Épardaud <stef@epardaud.fr>
 */
public class InterfaceVisitor extends Visitor {

    private Set<String> localCompanionClasses = new HashSet<String>();
    
    Target target;
    
    public InterfaceVisitor(Target target) {
        this.target = target;
    }
    
    private void collect(Node that, Interface model) {
        if(model != null && !model.isMember()){
            String name = Naming.suffixName(Naming.Suffix.$impl, model.getName());
            // find an unused name
            int i;
            String prefixedName;
            for(i=1;localCompanionClasses.contains(prefixedName = i+name);i++){}
            // add it
            localCompanionClasses.add(prefixedName);
            model.setJavaCompanionClassName(prefixedName);
        }
    }

    @Override
    public void visit(Tree.AnyMethod that){
        Function model = that.getDeclarationModel();
        // locals and toplevels get a type generated for them
        if(!model.isMember() && !model.isToplevel()){
            Set<String> old = localCompanionClasses;
            localCompanionClasses = new HashSet<String>();
            super.visit(that);
            localCompanionClasses = old;
        }else{
            super.visit(that);
        }
    }

    @Override
    public void visit(Tree.AttributeGetterDefinition that){
        Value model = that.getDeclarationModel();
        // locals and toplevels get a type generated for them
        if(!model.isMember() && !model.isToplevel()){
            Set<String> old = localCompanionClasses;
            localCompanionClasses = new HashSet<String>();
            super.visit(that);
            localCompanionClasses = old;
        }else{
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.AttributeSetterDefinition that){
        Setter model = that.getDeclarationModel();
        // locals and toplevels get a type generated for them
        if(!model.isMember() && !model.isToplevel()){
            Set<String> old = localCompanionClasses;
            localCompanionClasses = new HashSet<String>();
            super.visit(that);
            localCompanionClasses = old;
        }else{
            super.visit(that);
        }
    }

    @Override
    public void visit(Tree.TypeAliasDeclaration that){
        // stop at aliases, do not collect them since we can never create any instance of them
        // and they are useless at runtime
    }

    @Override
    public void visit(Tree.ClassOrInterface that){
        ClassOrInterface model = that.getDeclarationModel();
        // stop at aliases, do not collect them since we can never create any instance of them
        // and they are useless at runtime
        if(!model.isAlias()){
            // we never need to collect other local declaration names since only interfaces compete in the $impl name range
            if(model instanceof Interface)
                collect(that, (Interface) model);

            Set<String> old = localCompanionClasses;
            localCompanionClasses = new HashSet<String>();
            super.visit(that);
            localCompanionClasses = old;
        }
        if(model instanceof Interface){
            boolean useDefaultMethods = useDefaultMethods(model);
            ((Interface)model).setUseDefaultMethods(useDefaultMethods);
            if (!useDefaultMethods) {
                ((Interface)model).setCompanionClassNeeded(isInterfaceWithCode(model));
            }
        }
    }

    private boolean useDefaultMethods(ClassOrInterface model) {
        if (model instanceof Interface 
                && model.isToplevel()
                && target.compareTo(Target.JDK1_8) >= 0) {
            for (Declaration member : model.getMembers()) {
                if (member instanceof ClassOrInterface) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isInterfaceWithCode(ClassOrInterface model) {
        for (Declaration member : model.getMembers()) {
            if (member instanceof TypeParameter) {
                continue;
            }
            if (member instanceof Functional) {
                for (ParameterList pl : ((Functional)member).getParameterLists()) {
                    for (Parameter p : pl.getParameters()) {
                        if (p.isDefaulted()) {
                            return true;
                        }
                    }
                }
            }
            // formal classes contain code
            if (!member.isFormal() || member instanceof com.redhat.ceylon.model.typechecker.model.Class) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visit(Tree.ObjectDefinition that){
        Value model = that.getDeclarationModel();
        // locals and toplevels get a type generated for them
        if(!model.isMember() && !model.isToplevel()){
            Set<String> old = localCompanionClasses;
            localCompanionClasses = new HashSet<String>();
            super.visit(that);
            localCompanionClasses = old;
        }else{
            super.visit(that);
        }
    }
}
