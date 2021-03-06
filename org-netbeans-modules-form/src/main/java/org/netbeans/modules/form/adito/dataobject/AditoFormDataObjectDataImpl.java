package org.netbeans.modules.form.adito.dataobject;

import de.adito.aditoweb.nbm.nbide.nbaditointerface.form.dataobject.IAditoFormDataObjectData;
import org.netbeans.api.actions.Openable;
import org.netbeans.modules.form.*;
import org.openide.awt.UndoRedo;
import org.openide.cookies.EditorCookie;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

import javax.swing.*;

/**
 * @author J. Boesl, 27.08.12
 */
public class AditoFormDataObjectDataImpl implements IAditoFormDataObjectData
{

  private DataObject dataObject;
  private FormEditorSupport formEditor;

  public AditoFormDataObjectDataImpl(DataObject pDataObject)
  {
    dataObject = pDataObject;
  }

  @Override
  public synchronized FormEditorSupport getFormEditorSupport()
  {
    if (formEditor == null)
    {
      FormServices services = Lookup.getDefault().lookup(FormServices.class);
      formEditor = (FormEditorSupport) services.createEditorSupport(dataObject);
    }
    return formEditor;
  }

  @Override
  public EditorCookie.Observable getObservableEditorCookie()
  {
    return getFormEditorSupport();
  }

  @Override
  public Openable getOpenable()
  {
    return new Openable()
    {
      @Override
      public void open()
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getFormEditorSupport().open();
          }
        });
      }
    };
  }

  @Override
  public UndoRedo.Manager getUndoRedoManager()
  {
    return getFormEditorSupport().getUndoRedoManager();
  }

  public void resetForm()
  {
    getFormEditorSupport().resetFormEditor();
  }

}
