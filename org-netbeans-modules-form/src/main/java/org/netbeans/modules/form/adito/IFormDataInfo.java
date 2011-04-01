package org.netbeans.modules.form.adito;

import de.adito.aditoweb.filesystem.datamodelfs.access.mechanics.field.IFieldAccess;
import org.jetbrains.annotations.*;
import org.netbeans.modules.form.FormProperty;
import org.netbeans.modules.form.layoutsupport.LayoutSupportDelegate;

/**
 * @author J. Boesl, 31.03.11
 */
public interface IFormDataInfo
{

  @Nullable
  public IFieldAccess getFieldAccessByFormPropertyName(@NotNull String pFormPropertyName);

  @Nullable
  public IFieldAccess<Object> getFieldAccess(@NotNull String pModelPropertyName);

  @Nullable
  public FormProperty getFormPropertyByModelPropertyName(@NotNull String pModelPropertyName);

}