package org.netbeans.modules.form.adito.perstistencemanager;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.NbAditoInterface;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.sync.*;
import org.netbeans.modules.form.*;
import org.openide.filesystems.FileObject;

/**
 * @author J. Boesl, 26.06.11
 */
public class AComponentInfo
{

  private Class<?> componentClass;
  private String componentName;
  private IFormComponentInfo modelPropProvider;

  private AComponentInfo(Class<?> pComponentClass, String pComponentName, IFormComponentInfo pModelPropProvider)
  {

    componentClass = pComponentClass;
    componentName = pComponentName;
    modelPropProvider = pModelPropProvider;
  }

  public Class<?> getComponentClass()
  {
    return componentClass;
  }

  public String getComponentName()
  {
    return componentName;
  }

  public IFormComponentInfo getModelPropProvider()
  {
    return modelPropProvider;
  }

  /**
   * Factory Methode.
   *
   * @param pModelFile              das FileObject, das die Modelkomponenten repr�sentiert.
   * @param pPersistenceManagerInfo APersistenceManagerInfo-Objekt. Hier werden Fehler abgelegt.
   * @return null, wenn �ber das FileObjekt kein ComponentInfo erstellt werden konnte.
   */
  public static AComponentInfo create(FileObject pModelFile, APersistenceManagerInfo pPersistenceManagerInfo)
  {
    IFormComponentInfoProvider propertyInfo = NbAditoInterface.lookup(IFormComponentInfoProvider.class);
    IFormComponentInfo modelPropProvider = propertyInfo.createModelPropProvider(pModelFile);
    IFormComponentPropertyMapping componentPropertyMapping = modelPropProvider.getFormPropertyMapping();
    String compName;
    String className;
    try
    {
      if (componentPropertyMapping == null)
      {
        return null;
      }
      compName = pModelFile.getNameExt(); // entspricht Namen des DatenModels.
      className = componentPropertyMapping.getComponentClass().getName();
    }
    catch (Exception e)
    {
      return null; // kein Fehler, aber auch keine Komponente.
    }

    // first load the component class
    Class<?> compClass;
    Throwable compEx = null;
    try
    {
      compClass = FormUtils.loadSystemClass(className);
      // Force creation of the default instance in the correct L&F context
      BeanSupport.getDefaultInstance(compClass);
    }
    catch (Exception ex)
    {
      compClass = InvalidComponent.class;
      compEx = ex;
    }
    catch (LinkageError ex)
    {
      compClass = InvalidComponent.class;
      compEx = ex;
    }
    if (compEx != null)
    { // loading the component class failed
      pPersistenceManagerInfo.getNonfatalErrors().add(compEx);
      return null;
    }
    return new AComponentInfo(compClass, compName, modelPropProvider);
  }
}