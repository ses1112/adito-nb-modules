package org.netbeans.modules.form.adito.perstistencemanager;


import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.layout.INonSwingContainer;
import org.netbeans.modules.form.*;

/**
 * Container der nicht-sichtbare Komponenten enth�lt.
 *
 * @author J. Boesl, 28.06.11
 */
public class RADNonVisualContainerVisualComponent extends RADVisualComponent implements ComponentContainer
{

  private NonVisComponentContainer<INonSwingContainer> nonvisContainer = new NonVisComponentContainer<INonSwingContainer>(
      INonSwingContainer.class)
  {
    @Override
    void assignParentComponent(RADComponent pComp)
    {
      pComp.setParentComponent(RADNonVisualContainerVisualComponent.this);
    }
  };


  @Override
  protected void setBeanInstance(Object pBeanInstance)
  {
    nonvisContainer.setBeanInstance(pBeanInstance);
    super.setBeanInstance(pBeanInstance);
  }

  INonSwingContainer getBeanInstanceTyped()
  {
    return nonvisContainer.getBeanInstance();
  }

  @Override
  public RADNonVisualContainerNonVisualComponent[] getSubBeans()
  {
    return nonvisContainer.getSubBeans();
  }

  @Override
  public void initSubComponents(RADComponent[] initComponents)
  {
    nonvisContainer.initSubComponents(initComponents);

  }

  @Override
  public void reorderSubComponents(int[] perm)
  {
    nonvisContainer.reorderSubComponents(perm);
  }

  @Override
  public void add(RADComponent comp)
  {
    nonvisContainer.add(comp);
  }

  @Override
  public void remove(RADComponent comp)
  {
    nonvisContainer.remove(comp);
  }

  @Override
  public int getIndexOf(RADComponent comp)
  {
    return nonvisContainer.getIndexOf(comp);
  }

}
