package org.netbeans.core.windows;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.windowsystem.IAlwaysOpenTopComponentRegistry;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

import java.util.*;

/**
 * @author J. Boesl, 10.11.2010
 */
@ServiceProvider(service = IAlwaysOpenTopComponentRegistry.class)
public class AlwaysOpenTopComponentRegistry implements IAlwaysOpenTopComponentRegistry
{

  private Set<TopComponent> tcs;


  public AlwaysOpenTopComponentRegistry()
  {
    tcs = new HashSet<TopComponent>();
  }


  static IAlwaysOpenTopComponentRegistry getDefault()
  {
    return Lookup.getDefault().lookup(IAlwaysOpenTopComponentRegistry.class);
  }

  static boolean canClose(TopComponent pTc)
  {
    return getDefault().contains(pTc) || pTc.canClose();
  }


  public void registerTopComponent(TopComponent pTc)
  {
    if (pTc == null)
      return;
    tcs.add(pTc);
  }

  public void unregisterTopComponent(TopComponent pTc)
  {
    if (pTc == null)
      return;
    tcs.remove(pTc);
  }

  public boolean contains(TopComponent pTc)
  {
    return tcs.contains(pTc);
  }

  public Collection<TopComponent> getRegisteredTopComponents()
  {
    return Collections.unmodifiableCollection(tcs);
  }

}
