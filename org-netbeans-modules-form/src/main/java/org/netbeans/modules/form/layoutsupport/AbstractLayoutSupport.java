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

package org.netbeans.modules.form.layoutsupport;

import org.netbeans.modules.form.*;
import org.openide.ErrorManager;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;

import java.awt.*;
import java.beans.*;
import java.util.*;
import java.util.List;

/**
 * Default implementation of LayoutSupportDelegate interface. This class
 * implements most of general methods of LayoutSupportDelegate and introduces
 * other more specific methods which can be easily customized in subclasses.
 * <p/>
 * This class provides basic support for layouts following these rules:
 * (1) the supported layout manager is a JavaBean (it means that it has an
 * empty public constructor, the parameters are set as properties,
 * BeanInfo is used to obtain the properties),
 * (2) Container.setLayout(LayoutManager) method is used on the container to
 * set the layout,
 * (3) Container.add(Component) and Container.add(Component, Object) methods
 * are used on the container to add components (the second method for
 * adding with layout constraints).
 * <p/>
 * To create basic support for such a layout manager, it's enough to implement
 * getSupportedClass method only.
 * <p/>
 * Note that the subclass should have public constructor without parameters,
 * otherwise it should override cloneSupportInstance method.
 * <p/>
 * Note that the default implementation does not (and even cannot) provide any
 * working support for layout constraints of components in general - this must
 * be implemented in the sublasses individually. See BorderLayoutSupport for an
 * example of using simple value constraints, AbsoluteLayoutSupport for complex
 * object constraints (created by constructor), GridBagLayoutSupport for complex
 * constraints with special initialization code.
 *
 * @author Tomas Pavek
 */

public abstract class AbstractLayoutSupport implements LayoutSupportDelegate
{

  // ------

  private LayoutSupportContext layoutContext;

  private List<RADVisualComponent> radComponents = new ArrayList<RADVisualComponent>();
  private List<LayoutConstraints> componentConstraints = new ArrayList<LayoutConstraints>();

  private MetaLayout metaLayout;
  private FormProperty[] allProperties;

  // ------------------
  // LayoutSupportDelegate interface implementation

  /**
   * Initialization of the layout delegate before the first use.
   * There are three types of initialization supported:
   * (1) default initialization for an empty (newly created) layout
   * (lmInstance == null, fromCode == false),
   * (2) initialization from an already existing instance of LayoutManager
   * (lmInstance != null, fromCode == false),
   * (3) initialization from persistent code structure,
   * (lmInstance == null, fromCode == true).
   *
   * @param layoutContext provides a necessary context information for the
   *                      layout delegate
   * @param lmInstance    LayoutManager instance for initialization (may be null)
   * @throws Exception Exception occurred during initialization
   */
  @Override
  public void initialize(LayoutSupportContext layoutContext, LayoutManager lmInstance) throws Exception
  {
    if (this.layoutContext == layoutContext)
      return;

    this.layoutContext = layoutContext;
    clean();

    Class cls = getSupportedClass();
    if (cls != null && LayoutManager.class.isAssignableFrom(cls))
    {
      // create MetaLayout to manage layout manager as a bean
      if (lmInstance == null || !lmInstance.getClass().equals(cls))
      {
        // no valid layout manager instance - create a default one
        lmInstance = createDefaultLayoutInstance();
      }

      if (lmInstance != null)
      {
        metaLayout = new MetaLayout(this, lmInstance);
        deriveChangedPropertiesFromInstance(metaLayout);
      }
    }
    else
      metaLayout = null;
  }

  protected void deriveChangedPropertiesFromInstance(MetaLayout metaLayout)
  {
  }

  /**
   * States whether this support class is dedicted to some special container.
   *
   * @return true if only certain container is supported,
   *         false if a layout manager for use in any container is supported
   */
  @Override
  public boolean isDedicated()
  {
    Class cls = getSupportedClass();
    return cls != null && !LayoutManager.class.isAssignableFrom(cls);
  }

  /**
   * For dedicated supports: check whether given default container instance
   * is empty. Default implementation returns true - it's up to subcalsses
   * to check the special containers.
   *
   * @param cont default instance of Container
   * @return true if the container can be used as default (empty) instance
   *         with this layout support
   */
  @Override
  public boolean checkEmptyContainer(Container cont)
  {
    return true;
  }

  /**
   * Indicates whether the layout should be presented as a node in Component
   * Inspector (for setting properties). The node is provided for layout
   * managers typically (except null layou), and not for dedicated containers
   * support.
   *
   * @return whether a node should be created for the layout
   */
  @Override
  public boolean shouldHaveNode()
  {
    Class cls = getSupportedClass();
    return cls == null || LayoutManager.class.isAssignableFrom(cls);
  }

  /**
   * Provides a display name for the layout node - derived from the name
   * of supported class here.
   *
   * @return display name of supported layout
   */
  @Override
  public String getDisplayName()
  {
    Class cls = getSupportedClass();
    String name;

    if (cls != null)
    {
      name = cls.getName();
      int lastdot = name.lastIndexOf('.');
      if (lastdot > 0)
        name = name.substring(lastdot + 1);
    }
    else name = "null"; // NOI18N

    return name;
  }

  /**
   * Provides an icon to be used for the layout node in Component
   * Inspector. Only 16x16 color icon is required. The default implementation
   * tries to obtain the icon from BeanInfo of the layout manager.
   *
   * @param type is one of BeanInfo constants: ICON_COLOR_16x16,
   *             ICON_COLOR_32x32, ICON_MONO_16x16, ICON_MONO_32x32
   * @return icon to be used for layout node
   */
  @Override
  public Image getIcon(int type)
  {
    if (metaLayout != null)
    {
      Image icon = metaLayout.getBeanInfo().getIcon(type);
      if (icon != null)
        return icon;
    }

    switch (type)
    {
      case BeanInfo.ICON_COLOR_16x16:
      case BeanInfo.ICON_MONO_16x16:
        String iconURL = "org/netbeans/modules/form/layoutsupport/resources/AbstractLayout.gif";
        return ImageUtilities.loadImage(iconURL);
      default:
        String icon32URL = "org/netbeans/modules/form/layoutsupport/resources/AbstractLayout32.gif";
        return ImageUtilities.loadImage(icon32URL);
    }
  }

  /**
   * This method provides properties of the supported layout - if it is
   * a JavaBean class implementing LayoutManager. The properties are obtained
   * from the BeanInfo of the layout manager. Note these are not properties
   * of individual components constraints.
   *
   * @return properties of supported layout
   */
  @Override
  public Node.PropertySet[] getPropertySets()
  {
    Node.PropertySet[] propertySets;

    FormProperty[] properties = getProperties();
    if (properties == null)
    {
      propertySets = metaLayout != null ?
          metaLayout.getProperties() : null;
    }
    else
    { // a subclass provides special properties
      propertySets = new Node.PropertySet[1];
      propertySets[0] = new Node.PropertySet(
          "properties", // NOI18N
          FormUtils.getBundleString("CTL_PropertiesTab"), // NOI18N
          FormUtils.getBundleString("CTL_PropertiesTabHint")) // NOI18N
      {
        @Override
        public Node.Property[] getProperties()
        {
          return AbstractLayoutSupport.this.getProperties();
        }
      };
    }

    if (propertySets != null)
    {
      java.util.List<Node.Property> allPropsList = new ArrayList<Node.Property>();
      for (Node.PropertySet propertySet : propertySets)
      {
        Node.Property[] props = propertySet.getProperties();
        for (Node.Property prop : props)
          if (prop instanceof FormProperty)
            allPropsList.add(prop);
      }
      allProperties = new FormProperty[allPropsList.size()];
      allPropsList.toArray(allProperties);
    }
    else
    {
      allProperties = new FormProperty[0];
      propertySets = new Node.PropertySet[0];
    }

    return propertySets;
  }

  /**
   * Returns a class of a customizer for the layout manager being used as
   * a JavaBean. The class should be a java.awt.Component and
   * java.beans.Customizer. The default implementation tries to get the
   * customizer class from layout manager's BeanInfo.
   *
   * @return layout bean customizer class, null if no customizer is provided
   */
  @Override
  public Class getCustomizerClass()
  {
    return metaLayout == null ? null :
        metaLayout.getBeanInfo().getBeanDescriptor().getCustomizerClass();
  }

  /**
   * Returns an instance of a special customizer provided by the layout
   * delegate. This customizer need not implement java.beans.Customizer,
   * because its creation is under full control of the layout delegate - and
   * vice versa, the customizer can have full control over the layout
   * delegate (unlike the bean customizer which operates only with layout
   * manager bean instance). The default implementation returns null.
   *
   * @return instance of layout support customizer
   */
  @Override
  public Component getSupportCustomizer()
  {
    return null;
  }

  /**
   * Gets number of components in the layout.
   *
   * @return number of components in the layout
   */
  @Override
  public int getComponentCount()
  {
    return radComponents.size();
  }

  /**
   * This method is called to accept new components before they are added
   * to the layout (by calling addComponents method). It may adjust the
   * constraints, or refuse the components by throwing a RuntimeException
   * (e.g. IllegalArgumentException). It's up to the delagate to display an
   * error or warning message, the exception is not reported outside.
   * The default implementation accepts any components - simply does nothing.
   */
  @Override
  public void acceptNewComponents(RADVisualComponent[] components,
                                  LayoutConstraints[] constraints,
                                  int index)
  {
  }

  /**
   * This method is called after a property of the layout is changed by
   * the user. Subclasses may check whether the layout is valid after the
   * change and throw PropertyVetoException if the change should be reverted.
   * It's up to the delagate to display an error or warning message, the
   * exception is not reported outside. The default implementation accepts
   * any change.
   *
   * @param ev PropertyChangeEvent object describing the change
   */
  @Override
  public void acceptContainerLayoutChange(PropertyChangeEvent ev)
      throws PropertyVetoException
  {
    // as this method is called for each change, we update the layout
    // bean code here too
//        if (layoutBeanCode != null)
//            layoutBeanCode.updateCode();
  }

  /**
   * This method is called after a constraint property of some component
   * is changed by the user. Subclasses may check if the layout is valid
   * after the change and throw PropertyVetoException if the change should
   * be reverted. It's up to the delagate to display an error or warning
   * message, the exception is not reported outside. The default
   * implementation accepts any change.
   *
   * @param index index of the component in the layout
   * @param ev    PropertyChangeEvent object describing the change
   */
  @Override
  public void acceptComponentLayoutChange(int index, PropertyChangeEvent ev)
      throws PropertyVetoException
  {
  }

  /**
   * Adds new components to the layout. This is done just at the metadata
   * level, no real components but their CodeExpression representations
   * are added.
   * The code structures describing the layout is updated immediately.
   *
   * @param components     array of CodeExpression objects representing the
   *                       components to be accepted
   * @param newConstraints array of layout constraints of the components, may
   *                       contain nulls
   * @param index          position at which the components should be added (inserted);
   *                       if -1, the components should be added at the end
   */
  @Override
  public void addComponents(RADVisualComponent[] components,
                            LayoutConstraints[] newConstraints,
                            int index)
  {
    if (index < 0 || index > radComponents.size())
      index = radComponents.size();

    for (int i = 0; i < components.length; i++)
    {
      int ii = index + i;

      RADVisualComponent component = components[i];
      radComponents.add(ii, component);

      LayoutConstraints constr = newConstraints != null ? newConstraints[i] : null;
      if (constr == null)
        constr = createDefaultConstraints();

      componentConstraints.add(ii, constr);
    }
  }

  /**
   * Removes one component from the layout (at metadata level).
   * The code structures describing the layout is updated immediately.
   *
   * @param index index of the component in the layout
   */
  @Override
  public void removeComponent(int index)
  {
    radComponents.remove(index);
    componentConstraints.remove(index);
  }

  /**
   * Removes all components from the layout (at metadata level).
   * The code structures describing the layout is updated immediately.
   */
  @Override
  public void removeAll()
  {
    radComponents.clear();
    componentConstraints.clear();
  }

  /**
   * Gets layout constraints for a component at the given index.
   *
   * @param index index of the component in the layout
   * @return layout constraints of given component
   */
  @Override
  public LayoutConstraints getConstraints(int index)
  {
    return index < 0 || index >= componentConstraints.size() ? null : componentConstraints.get(index);
  }

  /**
   * This method is called when switching layout - giving an opportunity to
   * convert the previous constrainst of components to constraints of the new
   * layout (this layout). The default implementation does nothing.
   *
   * @param previousConstraints [input] layout constraints of components in
   *                            the previous layout (can be {@code null})
   * @param currentConstraints  [output] array of converted constraints for
   *                            the new layout - to be filled
   * @param components          [input] real components in a real container having the
   *                            previous layout
   */
  @Override
  public void convertConstraints(LayoutConstraints[] previousConstraints,
                                 LayoutConstraints[] currentConstraints,
                                 Component[] components)
  {
  }

  /**
   * Sets up the layout (without adding components) on a real container,
   * according to the internal metadata representation.
   *
   * @param container         instance of a real container to be set
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   */
  @Override
  public void setLayoutToContainer(Container container,
                                   Container containerDelegate)
  {
    if (isDedicated())
      return;

    LayoutManager lm = null;
    try
    {
      if (containerDelegate == layoutContext.getPrimaryContainerDelegate())
      {
        if (metaLayout != null) // use the instance of MetaLayout
          lm = (LayoutManager) metaLayout.getBeanInstance();
      }
      else
      { // use cloned layout instance
        lm = cloneLayoutInstance(container, containerDelegate);
      }
    }
    catch (Exception ex)
    { // should not happen
      ex.printStackTrace();
    }

    if (lm != null)
      containerDelegate.setLayout(lm);
  }

  /**
   * Adds real components to given container (according to layout
   * constraints stored for the components).
   *
   * @param container         instance of a real container to be added to
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param components        components to be added
   * @param index             position at which to add the components to container
   */
  @Override
  public void addComponentsToContainer(Container container,
                                       Container containerDelegate,
                                       Component[] components,
                                       int index)
  {
    for (int i = 0; i < components.length; i++)
    {
      LayoutConstraints constr = getConstraints(i + index);
      if (constr != null)
        containerDelegate.add(components[i],
                              constr.getConstraintsObject(),
                              i + index);
      else
        containerDelegate.add(components[i], i + index);
    }
  }

  /**
   * Removes a real component from a real container.
   *
   * @param container         instance of a real container
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param component         component to be removed
   * @return whether it was possible to remove the component (some containers
   *         may not support removing individual components reasonably)
   */
  @Override
  public boolean removeComponentFromContainer(Container container,
                                              Container containerDelegate,
                                              Component component)
  {
    containerDelegate.remove(component);
    component.setBounds(0, 0, 0, 0);
    return true;
  }

  /**
   * Removes all components from given real container.
   *
   * @param container         instance of a real container to be cleared
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @return whether it was possible to clear the container (some containers
   *         may not support this)
   */
  @Override
  public boolean clearContainer(Container container,
                                Container containerDelegate)
  {
    Component[] components = containerDelegate.getComponents();
    containerDelegate.removeAll();
    for (Component component : components) component.setBounds(0, 0, 0, 0);
    return true;
  }

  /**
   * This method is called when user clicks on the container in form
   * designer. The layout delegate may do something with the container,
   * e.g. for JTabbedPane it might switch the selected TAB. The default
   * implementation does nothing.
   *
   * @param p                 Point of click in the container
   * @param container         instance of the container when the click occurred
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   */
  @Override
  public void processMouseClick(Point p,
                                Container container,
                                Container containerDelegate)
  {
  }

  /**
   * This method is called when a component is selected in Component
   * Inspector. If the layout delegate is interested in such information,
   * it may store it and use it e.g. in arrangeContainer method.
   * The default implementation does nothing.
   *
   * @param index position (index) of the selected component in container
   */
  @Override
  public void selectComponent(int index)
  {
  }

  /**
   * In this method, the layout delegate has a chance to "arrange" real
   * container instance additionally - some other way that cannot be
   * done through layout properties and added components. For example, the
   * selected component index can be applied here (see delegates for
   * CardLayout and JTabbedPane). The default implementation does nothing.
   *
   * @param container         instance of a real container to be arranged
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   */
  @Override
  public void arrangeContainer(Container container,
                               Container containerDelegate)
  {
  }

  /**
   * This method should calculate layout constraints for a component dragged
   * over a container (or just for mouse cursor being moved over container,
   * without any component). This method is useful for "constraints oriented"
   * layout managers (like e.g. BorderLayout or GridBagLayout).
   *
   * @param container         instance of a real container over/in which the
   *                          component is dragged
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param component         the real component being dragged, can be null
   * @param index             position (index) of the component in its current container;
   *                          -1 if there's no dragged component
   * @param posInCont         position of mouse in the container delegate
   * @param posInComp         position of mouse in the dragged component; null if
   *                          there's no dragged component
   * @return new LayoutConstraints object corresponding to the position of
   *         the component in the container; may return null if the layout
   *         does not use component constraints, or if default constraints
   *         should be used
   */
  @Override
  public LayoutConstraints getNewConstraints(Container container,
                                             Container containerDelegate,
                                             Component component,
                                             int index,
                                             Point posInCont,
                                             Point posInComp)
  {
    return null;
  }

  /**
   * This method should calculate position (index) for a component dragged
   * over a container (or just for mouse cursor being moved over container,
   * without any component). This method is useful for layout managers that
   * don't use component constraints (like e.g. FlowLayout or GridLayout)
   *
   * @param container         instance of a real container over/in which the
   *                          component is dragged
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param component         the real component being dragged, can be null
   * @param index             position (index) of the component in its current container;
   *                          -1 if there's no dragged component
   * @param posInCont         position of mouse in the container delegate
   * @param posInComp         position of mouse in the dragged component; null if
   *                          there's no dragged component
   * @return index corresponding to the position of the component in the
   *         container; may return -1 if the layout rather uses component
   *         constraints, or if a default index should be used
   */
  @Override
  public int getNewIndex(Container container,
                         Container containerDelegate,
                         Component component,
                         int index,
                         Point posInCont,
                         Point posInComp)
  {
    return -1;
  }

  /**
   * Returns context for the assistant (context sensitive help bar).
   *
   * @return context for the assistant.
   */
  public String getAssistantContext()
  {
    return null;
  }

  /**
   * Returns context parameters for the assistant (context sensitive help bar).
   *
   * @return context parameters for the assistant.
   */
  public Object[] getAssistantParams()
  {
    return null;
  }

  /**
   * This method should paint a feedback for a component dragged over
   * a container (or just for mouse cursor being moved over container,
   * without any component). In principle, it should present given component
   * layout constraints or index graphically.
   *
   * @param container         instance of a real container over/in which the
   *                          component is dragged
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame) - here the feedback is painted
   * @param component         the real component being dragged, can be null
   * @param newConstraints    component layout constraints to be presented
   * @param newIndex          component's index position to be presented
   *                          (if newConstraints == null)
   * @param g                 Graphics object for painting (with color and line style set)
   * @return whether any feedback was painted (may return false if the
   *         constraints or index are invalid, or if the painting is not
   *         implemented)
   */
  @Override
  public boolean paintDragFeedback(Container container,
                                   Container containerDelegate,
                                   Component component,
                                   LayoutConstraints newConstraints,
                                   int newIndex,
                                   Graphics g)
  {
    return false;
  }

  /**
   * Provides resizing options for given component. It can combine the
   * bit-flag constants RESIZE_UP, RESIZE_DOWN, RESIZE_LEFT, RESIZE_RIGHT.
   *
   * @param container         instance of a real container in which the
   *                          component is to be resized
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param component         real component to be resized
   * @param index             position of the component in its container
   * @return resizing options for the component; 0 if no resizing is possible
   */
  @Override
  public int getResizableDirections(Container container,
                                    Container containerDelegate,
                                    Component component,
                                    int index)
  {
    return 0;
  }

  /**
   * This method should calculate layout constraints for a component being
   * resized.
   *
   * @param container         instance of a real container in which the
   *                          component is resized
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @param component         real component being resized
   * @param index             position of the component in its container
   * @param sizeChanges       Insets object with size differences
   * @param posInCont         position of mouse in the container delegate
   * @return component layout constraints for resized component; null if
   *         resizing is not possible or not implemented
   */
  @Override
  public LayoutConstraints getResizedConstraints(Container container,
                                                 Container containerDelegate,
                                                 Component component,
                                                 int index,
                                                 Rectangle originalBounds,
                                                 Insets sizeChanges,
                                                 Point posInCont)
  {
    return null;
  }

  /**
   * Cloning method - creates a copy of the layout delegate.
   *
   * @param targetContext LayoutSupportContext for the new layout delegate
   * @param components    array of RADVisualComponent objects representing the
   *                      components for the new layout delegate (corresponding to the
   *                      current ones)
   * @return cloned layout delegate instance
   */
  @Override
  public LayoutSupportDelegate cloneLayoutSupport(LayoutSupportContext targetContext, RADVisualComponent[] components)
  {
    AbstractLayoutSupport clone = createLayoutSupportInstance();
    try
    {
      clone.initialize(targetContext, null);
    }
    catch (Exception ex)
    { // should not fail (not reading from code)
      ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ex);
      return null;
    }

    FormProperty[] sourceProperties = getAllProperties();
    FormProperty[] targetProperties = clone.getAllProperties();
    FormUtils.copyProperties(sourceProperties,
                             targetProperties,
                             FormUtils.CHANGED_ONLY
                                 | FormUtils.DISABLE_CHANGE_FIRING);

    int compCount = getComponentCount();
    LayoutConstraints[] constraints = new LayoutConstraints[compCount];
    for (int i = 0; i < compCount; i++)
    {
      LayoutConstraints constr = getConstraints(i);
      constraints[i] = constr != null ? constr.cloneConstraints() : null;
    }

    clone.addComponents(components, constraints, 0);

    return clone;
  }

  // ------------------
  // extended API for AbstractLayoutSupport subclasses

  /**
   * Creates a default instance of LayoutManager (for internal use).
   * Override this method if the layout manager is not a bean (cannot
   * be created from default constructor).
   *
   * @return new (default) instance of supported layout manager
   */
  protected LayoutManager createDefaultLayoutInstance() throws Exception
  {
    return (LayoutManager)
        CreationFactory.createDefaultInstance(getSupportedClass());
  }

  /**
   * Cloning method - creates a clone of the reference LayoutManager
   * instance (for external use). Override this method if the layout manager
   * is not a bean (cannot be created from default constructor, and copied
   * using properties).
   *
   * @param container         instance of a real container in whose container
   *                          delegate the layout manager will be probably used
   * @param containerDelegate effective container delegate of the container
   *                          (e.g. like content pane of JFrame)
   * @return new instance of layout manager representing the layout (with
   *         all properties set)
   */
  protected LayoutManager cloneLayoutInstance(Container container,
                                              Container containerDelegate)
      throws Exception
  {
    return metaLayout == null ? null :
        (LayoutManager) metaLayout.cloneBeanInstance(null);
  }

  /**
   * Cloning method - creates a new instance of this layout support, just
   * not initialized yet.
   *
   * @return new instance of this layout support
   */
  protected AbstractLayoutSupport createLayoutSupportInstance()
  {
    try
    {
      return getClass().newInstance();
    }
    catch (Exception ex)
    { // should not happen for AbstractLayoutSupport subclasses
      return null;
    }
  }

  /**
   * Cleans all data before the delegate is initialized.
   */
  protected void clean()
  {
    radComponents.clear();
    componentConstraints.clear();
    metaLayout = null;
    allProperties = null;
  }

  /**
   * This method is called to get a default component layout constraints
   * metaobject in case it is not provided (e.g. in addComponents method).
   *
   * @return the default LayoutConstraints object for the supported layout;
   *         null if no component constraints are used
   */
  protected LayoutConstraints createDefaultConstraints()
  {
    return null; // no default implementation possible
  }

  /**
   * Method to obtain just one propetry of given name.
   *
   * @return layout property of given name
   */
  protected Node.Property getProperty(String propName)
  {
    return metaLayout == null ? null :
        metaLayout.getPropertyByName(propName);
  }

  /**
   * This method can be overridden to provide other layout properties than
   * the standard ones of LayoutManager handled automatically as a bean.
   * This method is called from getPropertySets() implementation to obtain
   * the default property set for the layout (assuming there's only one
   * property set). So it is also possible to override (more generally)
   * getPropertySets() instead.
   *
   * @return array of alternative properties of the layout
   */
  protected FormProperty[] getProperties()
  {
    return null; // use default "bean" properties
  }

  // ---------------
  // useful methods for subclasses

  /**
   * Gets the LayoutSupportContext instance set in initialize method.
   *
   * @return the attached LayoutSupportContext object providing necessary
   *         context information
   */
  protected final LayoutSupportContext getLayoutContext()
  {
    return layoutContext;
  }

  /**
   * Gets the internal list of layout constraints of components in the
   * layout. (The list contains instances of LayoutConstraints).
   *
   * @return list of internally stored layout constraints of components
   */
  protected final java.util.List<LayoutConstraints> getConstraintsList()
  {
    return componentConstraints;
  }

  /**
   * This methods collects properties from all property sets to one array.
   *
   * @return all properties of the layout in an array
   */
  protected final FormProperty[] getAllProperties()
  {
    if (allProperties == null)
      getPropertySets();

    return allProperties;
  }

  /**
   * This method should be used by subclasses if they need to re-create
   * the reference layout manager instance (see BoxLayoutSupport for example).
   */
  protected final void updateLayoutInstance()
  {
    Container cont = layoutContext.getPrimaryContainer();
    Container contDel = layoutContext.getPrimaryContainerDelegate();

    LayoutManager lm = null;
    try
    {
      lm = cloneLayoutInstance(cont, contDel);
    }
    catch (Exception ex)
    { // should not happen
      ex.printStackTrace();
    }

    if (lm != null && metaLayout != null)
      metaLayout.updateInstance(lm);

    layoutContext.updatePrimaryContainer();
  }

  // ---------
  // utility methods

  /**
   * Used only internally.
   */
  protected static ResourceBundle getBundle()
  {
    return org.openide.util.NbBundle.getBundle(AbstractLayoutSupport.class);
  }

}
