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

package org.netbeans.modules.form.palette;

//import com.sun.source.tree.ClassTree;
//import com.sun.source.tree.Tree;
//import com.sun.source.util.TreePath;
import java.lang.ref.WeakReference;
import java.util.jar.*;
import java.util.*;
import java.io.*;
import java.lang.ref.Reference;
//import java.lang.reflect.Modifier;
import java.net.URL;
import java.text.MessageFormat;
//import java.util.logging.Level;
//import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
//import org.netbeans.api.java.source.CancellableTask;
//import org.netbeans.api.java.source.CompilationController;
//import org.netbeans.api.java.source.JavaSource;
//import org.netbeans.api.java.source.JavaSource.Phase;
//import org.netbeans.modules.classfile.ClassFile;
//import org.netbeans.modules.classfile.Method;

import org.openide.*;
import org.openide.loaders.*;
import org.openide.nodes.Node;
import org.openide.filesystems.*;

import org.netbeans.modules.form.project.*;
import org.openide.util.Exceptions;

/**
 * This class provides methods for installing new items to Palete.
 *
 * @author Tomas Pavek
 */

public final class BeanInstaller {

    private static Reference<AddToPaletteWizard> wizardRef;

    private BeanInstaller() {
    }

    // --------

    /** Installs beans from given source type. Lets the user choose the source,
     * the beans, and the target category in a wizard. */
    public static void installBeans(Class<? extends ClassSource.Entry> sourceType) {
        AddToPaletteWizard wizard = getAddWizard();
        if (wizard.show(sourceType))
            createPaletteItems(wizard.getSelectedBeans(),
                               wizard.getSelectedCategory());
    }

    /** Installs beans represented by given nodes (selected by the user). Lets
     * the user choose the palette category. */
    public static void installBeans(Node[] nodes) {       
        final List<ClassSource> beans = new LinkedList<ClassSource>();
        final List<String> unableToInstall = new LinkedList<String>();
        final List<String> noBeans = new LinkedList<String>();
      for (Node node : nodes)
      {
        DataObject dobj = node.getCookie(DataObject.class);
        if (dobj == null)
          continue;

        final FileObject fo = dobj.getPrimaryFile();
        JavaClassHandler handler = new JavaClassHandler()
        {
          @Override
          public void handle(String className, String problem)
          {
            if (problem == null)
            {
              ClassSource classSource =
                  ClassPathUtils.getProjectClassSource(fo, className);
              if (classSource == null)
              {
                // Issue 47947
                unableToInstall.add(className);
              }
              else
              {
                beans.add(classSource);
              }
            }
            else
            {
              noBeans.add(className);
              noBeans.add(problem);
            }
          }
        };
        scanFileObject(fo.getParent(), fo, handler);
      }
        
        if (unableToInstall.size() > 0) {
            Iterator iter = unableToInstall.iterator();
            StringBuilder sb = new StringBuilder();
            while (iter.hasNext()) {
                sb.append(iter.next()).append(", "); // NOI18N
            }
            sb.delete(sb.length()-2, sb.length());
            String messageFormat = PaletteUtils.getBundleString("MSG_cannotInstallBeans"); // NOI18N
            String message = MessageFormat.format(messageFormat, sb.toString());
            NotifyDescriptor nd = new NotifyDescriptor.Message(message);
            DialogDisplayer.getDefault().notify(nd);
            if (beans.isEmpty()) return;
        }

        String message = null;
        if (beans.isEmpty()) {
            message = PaletteUtils.getBundleString("MSG_noBeansUnderNodes"); // NOI18N
        }
        if (!noBeans.isEmpty()) {
            Iterator<String> iter = noBeans.iterator();
            while (iter.hasNext()) {
                String className = iter.next();
                String format = iter.next();
                String msg = MessageFormat.format(format, className);
                if (message != null) {
                    message += '\n';
                } else {
                    message = ""; // NOI18N
                }
                message += msg;
            }
        }
        if (message != null) {
            NotifyDescriptor nd = new NotifyDescriptor.Message(message);
            DialogDisplayer.getDefault().notify(nd);
        }
        if (beans.isEmpty()) return;

        String category = CategorySelector.selectCategory();
        if (category == null)
            return; // canceled by user

        final FileObject categoryFolder = PaletteUtils.getPaletteFolder()
                                                       .getFileObject(category);
        try {
            FileUtil.runAtomicAction(
            new FileSystem.AtomicAction () {
                @Override
                public void run() {
                  for (ClassSource bean : beans)
                  {
                    ClassSource classSource = bean;
                    try
                    {
                      PaletteItemDataObject.createFile(categoryFolder, classSource);
                      // TODO check the class if it can be loaded?
                    }
                    catch (IOException ex)
                    {
                      ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                    }
                  }
                }
            });
        }
        catch (java.io.IOException ex) {} // should not happen
    }

    /** Finds available JavaBeans in given JAR files. Looks for beans
     * specified in the JAR manifest only.
     */
    static List<BeanInstaller.ItemInfo> findJavaBeansInJar(List<? extends ClassSource.Entry> entries) {
        Map<String,ItemInfo> beans = null;

        for (ClassSource.Entry entry : entries) {
            for (URL root : entry.getClasspath()) {
                URL jarU = FileUtil.getArchiveFile(root);
                if (jarU == null) {
                    continue;
                }
                // Handle e.g. nbinst protocol.
                FileObject jarFO = URLMapper.findFileObject(jarU);
                if (jarFO == null) {
                    continue;
                }
                File jarF = FileUtil.toFile(jarFO);
                if (jarF == null) {
                    continue;
                }
                Manifest mf;
                try {
                    JarFile jf = new JarFile(jarF);
                    try {
                        mf = jf.getManifest();
                    } finally {
                        jf.close();
                    }
                } catch (IOException x) {
                    Exceptions.printStackTrace(x);
                    continue;
                }
                if (mf == null) {
                    continue;
                }
                for (Map.Entry<String,Attributes> section : mf.getEntries().entrySet()) {
                    if (!section.getKey().endsWith(".class")) { // NOI18N
                        continue;
                    }
                    String value = section.getValue().getValue("Java-Bean"); // NOI18N
                    if (!"True".equalsIgnoreCase(value)) { // NOI18N
                        continue;
                    }
                    String classname = section.getKey().substring(0, section.getKey().length() - 6) // cut off ".class"
                            .replace('\\', '/').replace('/', '.');
                    if (classname.startsWith(".")) { // NOI18N
                        classname = classname.substring(1);
                    }
                    ItemInfo ii = new ItemInfo();
                    ii.classname = classname;
                    ii.entry = entry;
                    if (beans == null) {
                        beans = new HashMap<String,ItemInfo>(100);
                    }
                    beans.put(ii.classname, ii);
                }
            }
        }

        return beans != null ? new ArrayList<ItemInfo>(beans.values()) : null;
    }

    /** Collects all classes under given roots that could be used as JavaBeans.
     * This method is supposed to search in JAR files or folders containing
     * built classes.
     */
    static List<ItemInfo> findJavaBeans(List<? extends ClassSource.Entry> entries) {
        Map<String,ItemInfo> beans = new HashMap<String,ItemInfo>(100);

        for (ClassSource.Entry entry : entries) {
            for (URL root : entry.getClasspath()) {
                FileObject foRoot = URLMapper.findFileObject(root);
                if (foRoot != null) {
                    scanFolderForBeans(foRoot, beans, entry);
                }
            }
        }

        return new ArrayList<ItemInfo>(beans.values());
    }

    // --------
    // private methods

    /** Installs given beans (described by ItemInfo in array). */
    private static void createPaletteItems(final ItemInfo[] beans,
                                           String category)
    {
        if (beans.length == 0)
            return;

        final FileObject categoryFolder =
            PaletteUtils.getPaletteFolder().getFileObject(category);
        if (categoryFolder == null)
            return;

        try {
            FileUtil.runAtomicAction(
            new FileSystem.AtomicAction () {
                @Override
                public void run() {
                  for (ItemInfo bean : beans)
                    try
                    {
                      PaletteItemDataObject.createFile(
                          categoryFolder,
                          new ClassSource(bean.classname,
                                          bean.entry));
                      // TODO check the class if it can be loaded?
                    }
                    catch (IOException ex)
                    {
                      ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
                    }
                }
            });
        }
        catch (java.io.IOException ex) {} // should not happen
    }

    /** Recursive method scanning folders for classes (class files) that could
     * be JavaBeans. */
    private static void scanFolderForBeans(FileObject folder, final Map<String,ItemInfo> beans, final ClassSource.Entry root) {
        JavaClassHandler handler = new JavaClassHandler() {
            @Override
            public void handle(String className, String problem) {
                if (problem == null) {
                    ItemInfo ii = new ItemInfo();
                    ii.classname = className;
                    ii.entry = root;
                    beans.put(ii.classname, ii);
                }
            }
        };
                    
        FileObject[] files = folder.getChildren();
      for (FileObject fo : files)
      {
        if (fo.isFolder())
        {
          scanFolderForBeans(fo, beans, root);
        }
        else try
        {
          if ("class".equals(fo.getExt()) // NOI18N
              && (DataObject.find(fo) != null))
          {
            scanFileObject(folder, fo, handler);
          }
        }
        catch (DataObjectNotFoundException ex)
        {
        } // should not happen
      }
    }    
    
    private static void scanFileObject(FileObject folder, final FileObject fileObject, final JavaClassHandler handler) {
      // TODO: stripped
//        if ("class".equals(fileObject.getExt())) { // NOI18N
//            processClassFile(fileObject, handler);
//        } else if ("java".equals(fileObject.getExt())) { // NOI18N
//            processJavaFile(fileObject, handler);
//        }
    }     
    
    /**
     * finds bean's FQN if there is any.
     * @param file file to search a bean
     * @return null or the fqn 
     */
    public static String findJavaBeanName(FileObject file) {
        final String[] fqn = new String[1];
        scanFileObject(null, file, new JavaClassHandler() {
            @Override
            public void handle(String className, String problem) {
                if (problem == null) {
                    fqn[0] = className;
                }
            }
        });
        return fqn[0];
    }

  // TODO: stripped
//    private static void processJavaFile(final FileObject javaFO, final JavaClassHandler handler) {
//        try {
//            JavaSource js = JavaSource.forFileObject(javaFO);
//            js.runUserActionTask(new CancellableTask<CompilationController>() {
//                @Override
//                public void cancel() {
//                }
//
//                @Override
//                public void run(CompilationController ctrl) throws Exception {
//                    ctrl.toPhase(Phase.ELEMENTS_RESOLVED);
//                    TypeElement clazz = findClass(ctrl, javaFO.getName());
//                    if (clazz != null) {
//                        handler.handle(clazz.getQualifiedName().toString(), isDeclaredAsJavaBean(clazz));
//                    }
//                }
//            }, true);
//        } catch (IOException ex) {
//            Logger.getLogger(BeanInstaller.class.getClass().getName()).
//                    log(Level.SEVERE, javaFO.toString(), ex);
//        }
//    }

  // TODO: stripped
//    private static TypeElement findClass(CompilationController ctrl, String className) {
//        for (Tree decl : ctrl.getCompilationUnit().getTypeDecls()) {
//            if (className.equals(((ClassTree) decl).getSimpleName().toString())) {
//                TreePath path = ctrl.getTrees().getPath(ctrl.getCompilationUnit(), decl);
//                TypeElement clazz = (TypeElement) ctrl.getTrees().getElement(path);
//                return clazz;
//            }
//        }
//        return null;
//    }

  // TODO: stripped
//    private static void processClassFile(FileObject classFO, JavaClassHandler handler) {
//        try {
//            // XXX rewrite this to use javax.lang.model.element.* as soon as JavaSource introduce .class files support
//            InputStream is = null;
//            ClassFile clazz;
//            try {
//                is = classFO.getInputStream();
//                clazz = new ClassFile(is, false);
//            } finally {
//                if (is != null) {
//                    is.close();
//                }
//            }
//            if (clazz != null) {
//                handler.handle(clazz.getName().getExternalName(), isDeclaredAsJavaBean(clazz));
//            }
//        } catch (IOException ex) {
//            Logger.getLogger(BeanInstaller.class.getClass().getName()).
//                    log(Level.SEVERE, classFO.toString(), ex);
//        }
//
//    }
        
    public static String isDeclaredAsJavaBean(TypeElement clazz) {
        if (ElementKind.CLASS != clazz.getKind()) {
            return PaletteUtils.getBundleString("MSG_notAClass"); // NOI18N
        }

        Set<javax.lang.model.element.Modifier> mods = clazz.getModifiers();
        if (mods.contains(javax.lang.model.element.Modifier.ABSTRACT)) {
            return PaletteUtils.getBundleString("MSG_abstractClass"); // NOI18N
        }

        if (!mods.contains(javax.lang.model.element.Modifier.PUBLIC)) {
            return PaletteUtils.getBundleString("MSG_notPublic"); // NOI18N
        }
        
        for (Element member : clazz.getEnclosedElements()) {
            mods = member.getModifiers();
            if (ElementKind.CONSTRUCTOR == member.getKind() &&
                    mods.contains(javax.lang.model.element.Modifier.PUBLIC) &&
                    ((ExecutableElement) member).getParameters().isEmpty()) {
                return null;
            }
        }
        
        return PaletteUtils.getBundleString("MSG_noPublicConstructor"); // NOI18N
    }

  // TODO: stripped
//    public static String isDeclaredAsJavaBean(ClassFile clazz) {
//        int access = clazz.getAccess();
//
//        if (Modifier.isInterface(access) || clazz.isAnnotation() ||
//                clazz.isEnum() || clazz.isSynthetic()) {
//            return PaletteUtils.getBundleString("MSG_notAClass"); // NOI18N
//        }
//
//        if (Modifier.isAbstract(access)) {
//            return PaletteUtils.getBundleString("MSG_abstractClass"); // NOI18N
//        }
//
//        if (!Modifier.isPublic(access)) {
//            return PaletteUtils.getBundleString("MSG_notPublic"); // NOI18N
//        }
//
//        for (Object omethod : clazz.getMethods()) {
//            Method method = (Method) omethod;
//            if (method.isPublic() && method.getParameters().isEmpty() &&
//                    "<init>".equals(method.getName())) { // NOI18N
//                return null;
//            }
//        }
//        return PaletteUtils.getBundleString("MSG_noPublicConstructor"); // NOI18N
//    }
    
    private static AddToPaletteWizard getAddWizard() {
        AddToPaletteWizard wizard = null;
        if (wizardRef != null)
            wizard = wizardRef.get();
        if (wizard == null) {
            wizard = new AddToPaletteWizard();
            wizardRef = new WeakReference<AddToPaletteWizard>(wizard);
        }
        return wizard;
    }

    // --------

    static class ItemInfo implements Comparable<ItemInfo> {
        String classname;
        ClassSource.Entry entry;

        @Override
        public int compareTo(ItemInfo ii) {
            int i;
            i = classname.lastIndexOf('.');
            String name1 = i >= 0 ? classname.substring(i+1) : classname;
            i = ii.classname.lastIndexOf('.');
            String name2 = i >= 0 ? ii.classname.substring(i+1) : ii.classname;
            return name1.compareTo(name2);
        }
    }
    
    private interface JavaClassHandler {        
        public void handle(String className, String problem);        
    }
    
}
