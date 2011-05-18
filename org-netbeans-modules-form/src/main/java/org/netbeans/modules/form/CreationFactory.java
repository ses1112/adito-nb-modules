/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.modules.form;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;

import org.netbeans.modules.form.fakepeer.FakePeerSupport;

/**
 * Factory class for creating objects, providing java creation code,
 * registering CreationDescriptor classes, and related utility methods.
 *
 * @author Tomas Pavek
 */

public class CreationFactory {

    private static Map<String,CreationDescriptor> registry;

    private static boolean defaultDescriptorsCreated = false;

    interface PropertyParameters {
     
        public String getPropertyName();

      public Class[] getPropertyParametersTypes();
    }
    
    static class Property2ParametersMapper {              
        
        private final String propertyName;        
        private final Class[] propertyType = new Class[1]; 
        private PropertyParameters parameters; 
        
        Property2ParametersMapper(Class propertyClass, String propertyName) {
            this.propertyType[0] = propertyClass;
            this.propertyName = propertyName;                  
        }                
        
        public String getPropertyName() {
            return  propertyName;
        }        

        public Class[] getPropertyTypes() {
            if(parameters!=null){
                return parameters.getPropertyParametersTypes();
            }            
            return propertyType;
        }

      public void setPropertyParameters(PropertyParameters parameters) {
            this.parameters = parameters;
        }                        
    }    
    
    private CreationFactory() {}

    // -----------
    // registry methods

    public static CreationDescriptor getDescriptor(Class cls) {
        CreationDescriptor cd = getRegistry().get(cls.getName());
        if (cd == null && !defaultDescriptorsCreated
                && (cls.getName().startsWith("javax.swing.") // NOI18N
                    || cls.getName().startsWith("java.awt."))) { // NOI18N
            createDefaultDescriptors();
            cd = getRegistry().get(cls.getName());
        }
        if (cd != null) {
            cd.setDescribedClass(cls);
        }
        return cd;
    }

    public static void registerDescriptor(CreationDescriptor desc) {
        getRegistry().put(desc.getDescribedClassName(), desc);
    }

  // -----------
    // creation methods

    public static Object createDefaultInstance(final Class cls)
        throws Exception
    {
        CreationDescriptor cd = getDescriptor(cls);
        Object cl = UIManager.get("ClassLoader"); // NOI18N
        ClassLoader systemCl = org.openide.util.Lookup.getDefault().lookup(ClassLoader.class);
        if (cl == systemCl) { // System classloader doesn't help to load user classes like JXLoginPanel
            UIManager.put("ClassLoader", null); // NOI18N
        }
        Object instance = cd != null ?
                              cd.createDefaultInstance() :
                              cls.newInstance();
        UIManager.put("ClassLoader", cl); // NOI18N
        initAfterCreation(instance);
        return instance;
    }

    public static Object createInstance(Class cls)
        throws Exception
    {
        Object instance;

        CreationDescriptor cd = CreationFactory.getDescriptor(cls);
        instance = cd != null ? cd.createDefaultInstance() :
                                cls.newInstance();

        initAfterCreation(instance);
        return instance;
    }

  // ------------
    // utility methods

  public static CreationDescriptor.Creator findCreator(
                                                 CreationDescriptor desc,
                                                 Class[] paramTypes)
    {
        CreationDescriptor.Creator[] creators = desc.getCreators();
      for (CreationDescriptor.Creator cr : creators)
      {
        if (cr.getParameterCount() == paramTypes.length)
        {
          Class[] types = cr.getParameterTypes();
          boolean match = true;
          for (int j = 0; j < types.length; j++)
            if (!types[j].isAssignableFrom(paramTypes[j]))
            {
              match = false;
              break;
            }
          if (match)
            return cr;
        }
      }
        return null;
    }

    /** Evaluates creators for array of properties.
     * (Useful for CreationDescriptor.findBestCreator(...) implementation.)
     * 
     * @param creators creators to consider
     * @param properties properties to consider
     * @param changedOnly determines whether to consider changed properties only
     * @return array of int - for each creator a count of placed properties
     */
    public static int[] evaluateCreators(CreationDescriptor.Creator[] creators,
                                         FormProperty[] properties,
                                         boolean changedOnly) {

        if (creators == null || creators.length == 0) return null;

        int[] placed = new int[creators.length];
      for (FormProperty property : properties)
      {
        if (!changedOnly || property.isChanged())
        {
          String name = property.getName();

          for (int j = 0; j < creators.length; j++)
          {
            String[] crNames = creators[j].getPropertyNames();
            for (String crName : crNames)
              if (name.equals(crName))
                placed[j]++;
          }
        }
      }
        return placed;
    }

    /** Finds the best creator upon given evaluation.
     * (Useful for CreationDescriptor.findBestCreator(...) implementation.)
     * 
     * @param creators creators to consider.
     * @param properties properties to consider.
     * @param placed for each creator a count of placed properties
     * @param placeAllProps determines whether all properties should be placed
     * @return index of most suitable creator
     */
    public static int getBestCreator(CreationDescriptor.Creator[] creators,
                                     FormProperty[] properties,
                                     int[] placed,
                                     boolean placeAllProps)
    {
        if (creators == null || creators.length == 0)
            return -1;

        int best = 0;
        int[] sizes = new int[creators.length];
        sizes[0] = creators[0].getParameterCount();

        if (placeAllProps) {
            // find shortest creator with all properties placed
            for (int i=1; i < placed.length; i++) {
                sizes[i] = creators[i].getParameterCount();
                if (placed[i] > placed[best]
                    || (placed[i] == placed[best]
                        && (sizes[i] < sizes[best]
                            || (sizes[i] == sizes[best]
                                && compareCreatorsAmbiguity(
                                     creators[i], creators[best], properties)
                                   == 1))))
                    best = i;
            }
        }
        else { // find longest creator with all parameters provided by properties
            for (int i=1; i < placed.length; i++) {
                sizes[i] = creators[i].getParameterCount();
                int iDiff = sizes[i] - placed[i];
                int bestDiff = sizes[best] - placed[best];
                if (iDiff < bestDiff
                    || (iDiff == bestDiff
                        && (sizes[i] > sizes[best]
                            || (sizes[i] == sizes[best]
                                && compareCreatorsAmbiguity(
                                     creators[i], creators[best], properties)
                                   == 1))))
                    best = i;
            }
        }

        return best;
    }

    // -----------
    // non-public methods

    /** Compares two creators with equal number of placed properties and equal
     * number of all properties. To distinguish which one is better, their
     * properties are checked for null values which could cause ambiguity in
     * generated code.
     * @return 1 if creator1 is better, 2 if creator2 is better, 0 if they
     *          are equal
     */
    static int compareCreatorsAmbiguity(CreationDescriptor.Creator cr1,
                                        CreationDescriptor.Creator cr2,
                                        FormProperty[] properties)
    {
        int nullValues1 = 0;
        int nullValues2 = 0;

        for (int i=0, n=cr1.getParameterCount(); i < n; i++) {
            String name1 = cr1.getPropertyNames()[i];
            String name2 = cr2.getPropertyNames()[i];
            if (!name1.equals(name2)) {
                FormProperty prop1 = null;
                FormProperty prop2 = null;
              for (FormProperty property : properties)
                if (prop1 == null && name1.equals(property.getName()))
                {
                  prop1 = property;
                  if (prop2 != null)
                    break;
                }
                else if (prop2 == null && name2.equals(property.getName()))
                {
                  prop2 = property;
                  if (prop1 != null)
                    break;
                }

                if (prop1 != null && !prop1.getValueType().isPrimitive()) {
                    try {
                        if (prop1.getRealValue() == null)
                            nullValues1++;
                    }
                    catch (Exception ex) {} // ignore
                }
                if (prop2 != null && !prop2.getValueType().isPrimitive()) {
                    try {
                        if (prop2.getRealValue() == null)
                            nullValues2++;
                    }
                    catch (Exception ex) {} // ignore
                }
            }
        }

        if (nullValues1 == nullValues2)
            return 0;
        return nullValues1 < nullValues2 ? 1 : 2;
    }

    static Map<String,CreationDescriptor> getRegistry() {
        if (registry == null)
            registry = new HashMap<String,CreationDescriptor>(40);
        return registry;
    }

    // additional initializations for some components - in fact hacks required
    // by using fake peers and remapping L&F...
    private static void initAfterCreation(Object instance) {
        if (instance instanceof javax.swing.border.TitledBorder)
            ((javax.swing.border.TitledBorder)instance)
                .setTitleFont(UIManager.getFont("TitledBorder.createDefaultInstancefont")); // NOI18N
        else if (instance instanceof java.awt.Component
                 && !(instance instanceof javax.swing.JComponent)
                 && !(instance instanceof javax.swing.RootPaneContainer))
        {
            ((Component)instance).setName(null);
            ((Component)instance).setFont(FakePeerSupport.getDefaultAWTFont());
        }
        else if (instance instanceof MenuComponent) {
            ((MenuComponent)instance).setName(null);
            ((MenuComponent)instance).setFont(FakePeerSupport.getDefaultAWTFont());
        }
    }

    // ---------------------------------------------------
    // constructors descriptors for some "special" classes...  
    private static void createDefaultDescriptors() {
        Class[][] constrParamTypes;
        String[][] constrPropertyNames;
        Object[] defaultConstrParams;
        String methodName;        
        CreationDescriptor cd;
        InsetsPropertyParameters[] insetsPropertyParameters = 
                new InsetsPropertyParameters[] { new InsetsPropertyParameters("borderInsets") };        
                
        try {
        // borders ------------

        // LineBorder                                       
        constrParamTypes = new Class[][] {
            { Color.class, Integer.TYPE , Boolean.TYPE }

        };
        constrPropertyNames = new String[][] {            
            { "lineColor", "thickness" , "roundedCorners" }
        };
                 
        defaultConstrParams = new Object[] { java.awt.Color.black };
        cd = new CreationDescriptor();
        cd.addConstructorCreators(  
                javax.swing.border.LineBorder.class, 
                constrParamTypes, constrPropertyNames, defaultConstrParams);
                
        constrParamTypes = new Class[][] {
            { Color.class },
            { Color.class, Integer.TYPE }
        };
        constrPropertyNames = new String[][] {
            { "lineColor" },
            { "lineColor", "thickness" }  
            
        };
        methodName = "createLineBorder";
        
        cd.addMethodCreators(
                javax.swing.BorderFactory.class, javax.swing.border.LineBorder.class, methodName,
                constrParamTypes, constrPropertyNames, null, defaultConstrParams);
        registerDescriptor(cd);
                
        
        // EtchedBorder
        defaultConstrParams = new Object[] { };        
        constrParamTypes = new Class[][] {
            { },
            { Color.class, Color.class },
            { Integer.TYPE },
            { Integer.TYPE, Color.class, Color.class }
        };
        constrPropertyNames = new String[][] {
            { },
            { "highlightColor", "shadowColor" },
            { "etchType" },
            { "etchType", "highlightColor", "shadowColor" }
        };
        
        methodName = "createEtchedBorder";    
        registerDescriptor(new CreationDescriptor(
                javax.swing.BorderFactory.class, javax.swing.border.EtchedBorder.class, methodName,
                constrParamTypes, constrPropertyNames, null, defaultConstrParams));
        
        // EmptyBorder     
        constrParamTypes = new Class[][] {
            { Insets.class }
        };
        constrPropertyNames = new String[][] {
            { "borderInsets" }
        };
        
        defaultConstrParams = new Object[] {1, 1, 1, 1};
        methodName = "createEmptyBorder";
        
        registerDescriptor(new CreationDescriptor(
                                   javax.swing.BorderFactory.class, javax.swing.border.EmptyBorder.class, 
                                   methodName, constrParamTypes, constrPropertyNames, insetsPropertyParameters, defaultConstrParams));

        // TitledBorder              
        constrParamTypes = new Class[][] {
            { String.class },
            { Border.class, String.class },
            { Border.class, String.class, Integer.TYPE, Integer.TYPE },
            { Border.class, String.class, Integer.TYPE, Integer.TYPE, Font.class },
            { Border.class, String.class, Integer.TYPE, Integer.TYPE, Font.class, Color.class },
            { Border.class }
        };
        constrPropertyNames = new String[][] {
            { "title" },
            { "border", "title" },
            { "border", "title", "titleJustification", "titlePosition" },
            { "border", "title", "titleJustification", "titlePosition", "titleFont" },
            { "border", "title", "titleJustification", "titlePosition", "titleFont", "titleColor" },
            { "border" }
        };
        
        defaultConstrParams = new Object[] { null, "", 0, 0};
        methodName = "createTitledBorder";                      
        registerDescriptor(new CreationDescriptor(
                javax.swing.BorderFactory.class, javax.swing.border.TitledBorder.class, methodName,
                constrParamTypes, constrPropertyNames, null, defaultConstrParams));

        // CompoundBorder          
        constrParamTypes = new Class[][] {
            { },
            { Border.class, Border.class }
        };
        constrPropertyNames = new String[][] {
            { },
            { "outsideBorder", "insideBorder" }
        };           
        
        defaultConstrParams = new Object[0];
        methodName = "createCompoundBorder";             
        registerDescriptor(new CreationDescriptor(
                javax.swing.BorderFactory.class, javax.swing.border.CompoundBorder.class, methodName,
                constrParamTypes, constrPropertyNames, null, defaultConstrParams));

        // BevelBorder
        constrParamTypes = new Class[][] {
            { Integer.TYPE },
            { Integer.TYPE, Color.class, Color.class },
            { Integer.TYPE, Color.class, Color.class, Color.class, Color.class }
        };
        constrPropertyNames = new String[][] {
            { "bevelType" },
            { "bevelType", "highlightOuterColor", "shadowOuterColor" },
            { "bevelType", "highlightOuterColor", "highlightInnerColor",
                           "shadowOuterColor", "shadowInnerColor" }
        };
                      
        defaultConstrParams = new Object[] {javax.swing.border.BevelBorder.RAISED};
        methodName = "createBevelBorder";                     
        registerDescriptor(new CreationDescriptor(
                javax.swing.BorderFactory.class, javax.swing.border.BevelBorder.class, methodName, 
                constrParamTypes, constrPropertyNames, null, defaultConstrParams));
                         
        // SoftBevelBorder
        constrParamTypes = new Class[][] {
            { Integer.TYPE },
            { Integer.TYPE, Color.class, Color.class },
            { Integer.TYPE, Color.class, Color.class, Color.class, Color.class }
        };
        constrPropertyNames = new String[][] {
            { "bevelType" },
            { "bevelType", "highlightOuterColor", "shadowOuterColor" },
            { "bevelType", "highlightOuterColor", "highlightInnerColor",
                           "shadowOuterColor", "shadowInnerColor" }
        };        
        registerDescriptor(new CreationDescriptor(
                javax.swing.border.SoftBevelBorder.class, 
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // MatteBorder              
        cd = new CreationDescriptor();

        constrParamTypes = new Class[][] {
            { Icon.class }            
        };
        constrPropertyNames = new String[][] {
            { "tileIcon" }        
        };         
        cd.addConstructorCreators( javax.swing.border.MatteBorder.class, 
                                   constrParamTypes, constrPropertyNames, defaultConstrParams);
        
        constrParamTypes = new Class[][] {
            { Insets.class, Icon.class },
            { Insets.class, Color.class }
        };
        constrPropertyNames = new String[][] {
            { "borderInsets", "tileIcon" },
            { "borderInsets", "matteColor" }
        };         
        defaultConstrParams = new Object[] {
            1, 1, 1, 1,
            java.awt.Color.black
        };        
        methodName = "createMatteBorder";                                
        cd.addMethodCreators(javax.swing.BorderFactory.class, javax.swing.border.MatteBorder.class, methodName,
                                constrParamTypes, constrPropertyNames, insetsPropertyParameters, defaultConstrParams);        
        registerDescriptor(cd);

        // layouts --------------

        // BorderLayout
        constrParamTypes = new Class[][] {
            { },
            { Integer.TYPE, Integer.TYPE }
        };
        constrPropertyNames = new String[][] {
            { },
            { "hgap", "vgap" }
        };
        defaultConstrParams = new Object[0];
        registerDescriptor(new CreationDescriptor(
                java.awt.BorderLayout.class,
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // FlowLayout
        constrParamTypes = new Class[][] {
            { },
            { Integer.TYPE },
            { Integer.TYPE, Integer.TYPE, Integer.TYPE }
        };
        constrPropertyNames = new String[][] {
            { },
            { "alignment" },
            { "alignment", "hgap", "vgap" },
        };
        registerDescriptor(new CreationDescriptor(
                java.awt.FlowLayout.class,
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // GridBagLayout
        constrParamTypes = new Class[][] {
            { }
        };
        constrPropertyNames = new String[][] {
            { }
        };
        registerDescriptor(new CreationDescriptor(
                java.awt.GridBagLayout.class,
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // GridLayout
        constrParamTypes = new Class[][] {
            { },
            { Integer.TYPE, Integer.TYPE },
            { Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE }
        };
        constrPropertyNames = new String[][] {
            { },
            { "rows", "columns" },
            { "rows", "columns", "hgap", "vgap" }
        };
        registerDescriptor(new CreationDescriptor(
                java.awt.GridLayout.class,
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // CardLayout
        constrParamTypes = new Class[][] {
            { },
            { Integer.TYPE, Integer.TYPE }
        };
        constrPropertyNames = new String[][] {
            { },
            { "hgap", "vgap" }
        };
        registerDescriptor(new CreationDescriptor(
                java.awt.CardLayout.class,
                constrParamTypes, constrPropertyNames, defaultConstrParams));

        // AWT --------

        // Dialog
        constrParamTypes = new Class[][] {
            { java.awt.Frame.class },
        };
        constrPropertyNames = new String[][] {
            { "owner" },
        };
        defaultConstrParams = new Object[] { new java.awt.Frame() };
        registerDescriptor(new CreationDescriptor(
            java.awt.Dialog.class,
            constrParamTypes, constrPropertyNames, defaultConstrParams));

        // other -------

        // JPanel on JDK 1.3 uses one instance of FlowLayout for all instances
        // created by default constructor - this causes problems
        registerDescriptor(
            new CreationDescriptor(javax.swing.JPanel.class) {
                @Override
                public Object createDefaultInstance() {
                    return new javax.swing.JPanel(new java.awt.FlowLayout());
                }
            }
        );

        // ----------

        defaultDescriptorsCreated = true;

        }
        catch (NoSuchMethodException ex) { // should not happen
            ex.printStackTrace();
        }
    }
    
    static class InsetsPropertyParameters implements PropertyParameters {              
           
        private static Class[] parameterTypes = new Class[] {Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE};
        private final String propertyName;
        
        public InsetsPropertyParameters(String propertyName) {
            this.propertyName = propertyName;
        }
        
        @Override
        public String getPropertyName() {
            return propertyName;
        }

      @Override
        public Class[] getPropertyParametersTypes() {
            return parameterTypes;
        }

    }
    
    
}
