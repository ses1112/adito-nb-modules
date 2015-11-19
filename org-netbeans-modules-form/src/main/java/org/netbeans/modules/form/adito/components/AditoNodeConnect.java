package org.netbeans.modules.form.adito.components;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.NbAditoInterface;
import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.sync.*;
import de.adito.propertly.core.spi.IPropertyPitProvider;
import org.jetbrains.annotations.Nullable;
import org.netbeans.modules.form.RADComponent;
import org.openide.nodes.*;
import org.openide.util.*;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * @author J. Boesl, 30.06.11
 */
public final class AditoNodeConnect
{

  private AditoNodeConnect()
  {
  }

  public static Image getIcon(RADComponent pComponent, final int pType)
  {
    return _resolve(pComponent, new _NodeC<Image>()
    {
      @Override
      public Image resolveNode(Node pNode)
      {
        return pNode.getIcon(pType);
      }
    });
  }

  public static String getDisplayName(RADComponent pComponent)
  {
    return _resolve(pComponent, new _NodeC<String>()
    {
      @Override
      public String resolveNode(Node pNode)
      {
        return pNode.getDisplayName();
      }
    });
  }

  public static String getName(RADComponent pComponent)
  {
    return _resolve(pComponent, new _NodeC<String>()
    {
      @Override
      public String resolveNode(Node pNode)
      {
        return pNode.getName();
      }
    });
  }

  @Nullable
  public static Sheet getSheet(RADComponent pComponent)
  {
    return _resolve(pComponent, pModel -> {
      IFormComponentInfoProvider compInfoProvider = NbAditoInterface.lookup(IFormComponentInfoProvider.class);
      IFormComponentInfo componentInfo = compInfoProvider.createComponentInfo(pModel);
      return componentInfo.createSheet();
    });
  }

  public static void addWeakPropertyChangeListener(RADComponent pComponent, final PropertyChangeListener pListener)
  {
    _resolve(pComponent, new _NodeC<Void>()
    {
      @Override
      public Void resolveNode(Node pNode)
      {
        pNode.addPropertyChangeListener(WeakListeners.propertyChange(pListener, pNode));
        return null;
      }
    });
  }

  public static List<Action> getActions(RADComponent pComponent, final boolean pContext)
  {
    Action[] actions = _resolve(pComponent, new _NodeC<Action[]>()
    {
      @Override
      public Action[] resolveNode(Node pNode)
      {
        return pNode.getActions(pContext);
      }
    });
    if (actions == null)
      return Collections.emptyList();
    return Arrays.asList(actions);
  }

  @Nullable
  public static Lookup getLookup(RADComponent pComponent)
  {
    return _resolve(pComponent, new _DataObjectC<Lookup>()
    {
      @Override
      public Lookup resolveDataObjectLookup(Lookup pDataObjectLookup)
      {
        return pDataObjectLookup;
      }
    });
  }

  public static INodePrivileges getPriveleges(RADComponent pComponent)
  {
    return _resolve(pComponent, new _DataObjectC<INodePrivileges>()
    {
      @Override
      public INodePrivileges resolveDataObjectLookup(Lookup pDataObjectLookup)
      {
        return new INodePrivileges()
        {
          @Override
          public boolean canDelete()
          {
            return true; //pDataObject.isDeleteAllowed(); // TODO: propertly
          }

          @Override
          public boolean canCopy()
          {
            return true; // pDataObject.isCopyAllowed(); // TODO: propertly
          }

          @Override
          public boolean canMove()
          {
            return true; // pDataObject.isMoveAllowed(); // TODO: propertly
          }

          @Override
          public boolean canRename()
          {
            return true; // pDataObject.isRenameAllowed(); // TODO: propertly
          }
        };
      }
    });
  }


  private static <T> T _resolve(RADComponent pComp, _PropertyPitProviderC<T> pC)
  {
    IPropertyPitProvider<?, ?, ?> model = pComp.getARADComponentHandler().getModel();
    if (model == null)
      return null;
    return pC.resolvePPP(model);
  }


  /**
   * Ausführungsbeschreibung auf Nodes.
   */
  private abstract static class _NodeC<T> implements _PropertyPitProviderC<T>
  {
    @Override
    public final T resolvePPP(IPropertyPitProvider<?, ?, ?> pModel)
    {
      return resolveNode(NbAditoInterface.lookup(IFormComponentInfoProvider.class)
                             .createComponentInfo(pModel).getNode());
    }

    public abstract T resolveNode(Node pNode);
  }

  /**
   * Ausführungsbeschreibung auf DataObjects.
   */
  private abstract static class _DataObjectC<T> implements _PropertyPitProviderC<T>
  {
    @Override
    public final T resolvePPP(IPropertyPitProvider<?, ?, ?> pModel)
    {
      return resolveDataObjectLookup(NbAditoInterface.lookup(IFormComponentInfoProvider.class)
                                         .createComponentInfo(pModel).getDataObjectLookup());
    }

    public abstract T resolveDataObjectLookup(Lookup pDataObjectLookup);
  }

  /**
   * Ausführungsbeschreibung auf FileObjects.
   */
  private interface _PropertyPitProviderC<T>
  {
    T resolvePPP(IPropertyPitProvider<?, ?, ?> pModel);
  }

}
