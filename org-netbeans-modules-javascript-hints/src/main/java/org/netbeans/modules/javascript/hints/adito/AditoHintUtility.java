package org.netbeans.modules.javascript.hints.adito;

import org.jetbrains.annotations.*;
import org.mozilla.nb.javascript.Node;
import org.netbeans.api.progress.*;
import org.netbeans.api.project.Project;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.core.*;
import org.netbeans.modules.csl.hints.GsfHintsFactory;
import org.netbeans.modules.csl.hints.infrastructure.GsfHintsManager;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.javascript.hints.infrastructure.JsHintsProvider;
import org.netbeans.modules.parsing.api.*;
import org.netbeans.modules.parsing.spi.*;
import org.netbeans.spi.editor.hints.*;
import org.openide.*;
import org.openide.filesystems.FileObject;
import org.openide.util.*;

import javax.swing.text.*;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * @author W.Glanzer, 01.10.2017
 */
class AditoHintUtility
{

  private static final ResourceBundle BUNDLE = NbBundle.getBundle(AditoHintUtility.class);
  private static final RequestProcessor PROC = new RequestProcessor("AditoHintUtility");

  /**
   * Implementiert alle HintFixes f�r eine Liste aus Sources
   *
   * @param pFileObjects          Sources, deren Fixe implementiert werden sollen
   * @param pShouldResolveHintFix Predicate um auszusagen, welche HintFixes implementiert werden sollen
   * @param pExceptionConsumer    Consumer wenn eine Exception auftritt
   * @param pRunAfterFix          Runnable das ausgef�hrt wird, wenn die implementierung fertig ist / abgebrochen wurde (auch bei Exception)
   * @param pCancellable          Cancellable das angibt, ob abgebrochen werden soll
   */
  public static void implementHintFixes(List<Source> pFileObjects, @NotNull Predicate<HintFix> pShouldResolveHintFix, @Nullable Consumer<Exception> pExceptionConsumer, @Nullable Consumer<List<HintFix>> pRunAfterFix, @Nullable CancellableImpl pCancellable)
  {
    CancellableImpl cancel = pCancellable != null ? pCancellable : new CancellableImpl();
    ArrayList<HintFix> notImplementableFixes = new ArrayList<>();
    for (Source source : new ArrayList<>(pFileObjects))
    {
      PROC.submit(() -> {
        _runImplement(source, new ArrayList<>(), cancel, pShouldResolveHintFix, pExceptionConsumer, () -> {
          pFileObjects.remove(source);
          if(pFileObjects.size() == 0 && pRunAfterFix != null)
            pRunAfterFix.accept(notImplementableFixes);
        }, notImplementableFixes);
      });
    }
  }

  /**
   * @return Wandelt eine Node in einen lesbaren String um, bei dem die Typen ausgegeben werden
   */
  public static String toDebugString(Node pNode, int pLevel, Document pDocument) throws BadLocationException
  {
    StringBuilder result = new StringBuilder();

    // meine Node printen
    for(int i = 0; i < pLevel; i++)
      result.append("  ");
    result.append(pNode.getType())
        .append(" -> '")
        .append(pDocument.getText(pNode.getSourceStart(), pNode.getSourceEnd() - pNode.getSourceStart()).replaceAll("\n", " \\\\n"))
        .append("'\n");

    // Kinder
    Node child = pNode.getFirstChild();
    while(child != null)
    {
      result.append(toDebugString(child, pLevel+1, pDocument));
      child = child.getNext();
    }

    return result.toString();
  }

  private static void _runImplement(Source pSource, List<Object> pFixesAlreadyImplemented, CancellableImpl pCancellable, @NotNull Predicate<HintFix> pShouldResolveHintFix,
                                    @Nullable Consumer<Exception> pExceptionConsumer, @Nullable Runnable pRunAfterFix, @NotNull List<HintFix> pNotImplementableFixes)
  {
    try
    {
      ParserManager.parse(Collections.singletonList(pSource), new UserTask()
      {
        @Override
        public void run(ResultIterator resultIterator)
        {
          if(!pCancellable.isCancelled())
          {
            try
            {
              if (resultIterator == null)
                return;

              for (Embedding e : resultIterator.getEmbeddings())
                run(resultIterator.getResultIterator(e));

              AbstractMap.SimpleImmutableEntry<Fix, Object> fixToImplement = _getFirstFixToImplement(resultIterator, pFixesAlreadyImplemented, pShouldResolveHintFix);
              if (fixToImplement != null)
              {
                try
                {
                  pFixesAlreadyImplemented.add(fixToImplement.getValue());

                  /*
                   * Fix l�sen
                   */
                  HintFix hintFix = _getHintFix(fixToImplement.getKey());
                  boolean result = true;
                  if(hintFix != null && hintFix instanceof IFixAllFixable)
                    result = ((IFixAllFixable) hintFix).implementAndReturn();
                  else
                    fixToImplement.getKey().implement();
                  if(!result)
                    pNotImplementableFixes.add(hintFix);

                  PROC.submit(() -> {
                    try
                    {
                      _runImplement(pSource, pFixesAlreadyImplemented, pCancellable, pShouldResolveHintFix, pExceptionConsumer, pRunAfterFix, pNotImplementableFixes);
                    }
                    catch (Exception e)
                    {
                      if (pExceptionConsumer != null)
                        pExceptionConsumer.accept(e);
                    }
                  });
                  return; // es gibt etwas neues zu tun -> Schleife abbrechen
                }
                catch (Exception e)
                {
                  if (pExceptionConsumer != null)
                    pExceptionConsumer.accept(e);
                }
              }
            }
            catch(Exception e)
            {
              if (pExceptionConsumer != null)
                pExceptionConsumer.accept(e);
            }
          }

          // Nichts mehr zu tun -> Fertig
          if (pRunAfterFix != null)
            pRunAfterFix.run();
        }
      });
    }
    catch (Exception e)
    {
      if (pExceptionConsumer != null)
        pExceptionConsumer.accept(e);
      
      if (pRunAfterFix != null)
        pRunAfterFix.run();
    }
  }

  /**
   * @return Liefert den n�chsten Fix, der abgearbeitet werden muss. <tt>null</tt> wenn kein Fix mehr vorhanden ist.
   * Verkleinert pFileObjects, wenn n�tig
   */
  @Nullable
  private static AbstractMap.SimpleImmutableEntry<Fix, Object> _getFirstFixToImplement(ResultIterator resultIterator, List<Object> pFixesAlreadyImplemented, @NotNull Predicate<HintFix> pShouldResolveHintFix)
  {
    ArrayList<ErrorDescription> errors = new ArrayList<>();
    _collectHints(resultIterator, errors, resultIterator.getSnapshot());

    for (ErrorDescription description : errors)
    {
      for (Fix fix : description.getFixes().getFixes())
      {
        Object fixID = _getHintFixId(fix, pShouldResolveHintFix);
        if(fixID != null && !pFixesAlreadyImplemented.contains(fixID))
          return new AbstractMap.SimpleImmutableEntry<>(fix, fixID);
      }
    }

    return null;
  }

  /**
   * @return Liefert den HintFix aus einem Fix
   */
  @Nullable
  private static HintFix _getHintFix(Fix pFix)
  {
    try
    {
      Field fixField = pFix.getClass().getDeclaredField("fix");
      fixField.setAccessible(true);

      return (HintFix) fixField.get(pFix);
    }
    catch (Exception ignored)
    {
      // Wenn kein "fix"-Feld vorhanden ist, dann handelt es sich nicht um einen gew�hnlichen HintFix und es kann nichts getan werden..
    }

    return null;
  }

  /**
   * @return Liefert die ID eines HintFixes um die Identit�t eines Fixes Instanz�bergreifend feststellen zu k�nnen
   */
  @Nullable
  private static Object _getHintFixId(Fix pFix, @NotNull Predicate<HintFix> pShouldResolveHintFix)
  {
    HintFix hintFix = _getHintFix(pFix);
    if (hintFix instanceof IFixAllFixable && pShouldResolveHintFix.test(hintFix))
      return ((IFixAllFixable) hintFix).getID();

    return null;
  }

  /**
   * Kopiert aus dem GsfHintsManager
   *
   * @see GsfHintsManager#collectHints(org.netbeans.modules.parsing.api.ResultIterator, java.util.List[], org.netbeans.modules.parsing.api.Snapshot)
   */
  private static void _collectHints(ResultIterator controller, List<ErrorDescription> allHints, Snapshot tls)
  {
    String mimeType = controller.getSnapshot().getMimeType();
    Language language = LanguageRegistry.getInstance().getLanguageByMimeType(mimeType);
    if (language == null)
    {
      return;
    }
    GsfHintsManager hintsManager = language.getHintsManager();

    ParserResult parserResult = null;
    try
    {
      Parser.Result pr = controller.getParserResult();
      if (pr instanceof ParserResult)
      {
        parserResult = (ParserResult) pr;
      }
    }
    catch (ParseException ignored)
    {
    }

    if (parserResult == null)
    {
      return;
    }

    RuleContext context = new JsHintsProvider().createRuleContext();
    context.manager = language.getHintsManager();
    context.parserResult = parserResult;
    context.caretOffset = -1;
    context.selectionStart = -1;
    context.selectionEnd = -1;
    context.doc = (BaseDocument) parserResult.getSnapshot().getSource().getDocument(true);
    if(context.doc == null)
      return;

    List<ErrorDescription>[] hints = new List[3];
    getHints(hintsManager, context, hints, tls);
    for (int i = 0; i < 3; i++)
    {
      if (hints[i] != null)
        allHints.addAll(hints[i]);
    }

    for (Embedding e : controller.getEmbeddings())
    {
      _collectHints(controller.getResultIterator(e), allHints, tls);
    }
  }

  /**
   * Kopiert aus dem GsfHintsManager
   *
   * @see GsfHintsManager#getHints(org.netbeans.modules.csl.hints.infrastructure.GsfHintsManager, org.netbeans.modules.csl.api.RuleContext, java.util.List[], org.netbeans.modules.parsing.api.Snapshot)
   */
  private static void getHints(GsfHintsManager hintsManager, RuleContext context, List<ErrorDescription>[] ret, Snapshot tls)
  {
    if (hintsManager != null && context != null)
    {
      int caretPos = context.caretOffset;
      HintsProvider provider = new JsHintsProvider();

      // Force a refresh
      // HACK ALERT!
      List<Hint> descriptions = new ArrayList<>();
      if (caretPos == -1)
      {
        provider.computeHints(hintsManager, context, descriptions);
        List<ErrorDescription> result = ret[0] == null ? new ArrayList<>(descriptions.size()) : ret[0];
        for (int i = 0; i < descriptions.size(); i++)
        {
          Hint desc = descriptions.get(i);
          ErrorDescription errorDesc = hintsManager.createDescription(desc, context, true, i == descriptions.size() - 1);
          result.add(errorDesc);
        }

        ret[0] = result;
      }
      else
      {
        provider.computeSuggestions(hintsManager, context, descriptions, caretPos);
        List<ErrorDescription> result = ret[1] == null ? new ArrayList<>(descriptions.size()) : ret[1];
        for (int i = 0; i < descriptions.size(); i++)
        {
          Hint desc = descriptions.get(i);
          ErrorDescription errorDesc = hintsManager.createDescription(desc, context, true, i == descriptions.size() - 1);
          result.add(errorDesc);
        }

        ret[1] = result;
      }
    }
    try
    {
      ret[2] = GsfHintsFactory.getErrors(context.parserResult.getSnapshot(), context.parserResult, tls);
    }
    catch (ParseException ex)
    {
      Exceptions.printStackTrace(ex);
    }
  }

  /**
   * Cancellable-Reference
   */
  public static class CancellableImpl implements Cancellable
  {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public boolean cancel()
    {
      cancelled.set(true);
      return true;
    }

    public boolean isCancelled()
    {
      return cancelled.get();
    }
  }

  /**
   * HintFix um �ber ein gesammtes Projekt bestimmte Hints zu fixen
   */
  public abstract static class ImplementAllOfTypeFix implements HintFix
  {
    private final Project project;
    private final boolean showConfirmDialog;
    private final boolean displayFailedFixes;
    private final Class<? extends HintFix>[] fixes;

    @SafeVarargs
    public ImplementAllOfTypeFix(Project pProject, boolean pShowConfirmDialog, boolean pDisplayFailedFixes, Class<? extends HintFix>... pFixes)
    {
      project = pProject;
      showConfirmDialog = pShowConfirmDialog;
      displayFailedFixes = pDisplayFailedFixes;
      fixes = pFixes;
    }

    @Override
    public void implement()
    {
      implementOfType(fixes);
    }

    @Override
    public boolean isSafe()
    {
      return false;
    }

    @Override
    public boolean isInteractive()
    {
      return false;
    }

    protected void implementOfType(Class<? extends HintFix>[] pTypesToFix)
    {
      if(project == null)
        return;

      AditoHintUtility.CancellableImpl cancellable = new AditoHintUtility.CancellableImpl();
      ProgressHandle handle = ProgressHandleFactory.createSystemHandle(BUNDLE.getString("LBL_FixingHints"), cancellable);
      List<Class<? extends HintFix>> fixesList = Arrays.asList(pTypesToFix);
      BaseProgressUtils.showProgressDialogAndRun(new _ProgressRunnable(project, handle, cancellable, pFix -> fixesList.contains(pFix.getClass()), () -> getFileObjects(project), showConfirmDialog, displayFailedFixes), handle, true);
    }

    /**
     * @return <tt>null</tt> = alle FileObjects des Projektes
     */
    @Nullable
    protected List<FileObject> getFileObjects(@NotNull Project pProject)
    {
      return null;
    }

    private static class _ProgressRunnable implements Runnable
    {
      private final Project project;
      private final ProgressHandle handle;
      private final AditoHintUtility.CancellableImpl cancellable;
      private final Predicate<HintFix> shouldResolveHintFix;
      private final Supplier<List<FileObject>> fileObjectGetter;
      private final boolean showConfirmDialog;
      private final boolean displayFailedFixes;

      public _ProgressRunnable(Project pProject, ProgressHandle pHandle, AditoHintUtility.CancellableImpl pCancellable,
                               @NotNull Predicate<HintFix> pShouldResolveHintFix, @NotNull Supplier<List<FileObject>> pFileObjectGetter,
                               boolean pShowConfirmDialog, boolean pDisplayFailedFixes)
      {
        project = pProject;
        handle = pHandle;
        cancellable = pCancellable;
        shouldResolveHintFix = pShouldResolveHintFix;
        fileObjectGetter = pFileObjectGetter;
        showConfirmDialog = pShowConfirmDialog;
        displayFailedFixes = pDisplayFailedFixes;
      }

      @Override
      public void run()
      {
        handle.start();
        handle.switchToIndeterminate();
        List<FileObject> fileObjects = fileObjectGetter.get();
        List<Source> sources = (fileObjects != null ? fileObjects.stream() : _searchFileObject(project.getProjectDirectory(), pFo -> pFo.getMIMEType().equals("text/javascript"), cancellable).stream())
            .map(Source::create)
            .collect(Collectors.toList());
        int initSize = sources.size();

        // Sicherheitsabfrage, wenn gew�nscht
        if(showConfirmDialog)
        {
          String lbl_confirmFullFix = MessageFormat.format(BUNDLE.getString("LBL_ConfirmFullFix"), initSize, project.getProjectDirectory().getName());
          NotifyDescriptor.Confirmation confDescr = new DialogDescriptor.Confirmation(lbl_confirmFullFix, NotifyDescriptor.YES_NO_OPTION);
          if (DialogDisplayer.getDefault().notify(confDescr) != NotifyDescriptor.YES_OPTION)
            cancellable.cancel();
        }

        if(cancellable.isCancelled())
          handle.finish();
        else
        {
          handle.switchToDeterminate(initSize);
          AtomicBoolean running = new AtomicBoolean(true);
          AtomicReference<List<HintFix>> fixesFailed = new AtomicReference<>();
          implementHintFixes(sources, shouldResolveHintFix, Throwable::printStackTrace, (pFixesNotImplementable) -> {
            fixesFailed.set(pFixesNotImplementable);
            running.set(false);
            synchronized (running)
            {
              running.notifyAll();
            }
          }, cancellable);

          synchronized (running)
          {
            while (running.get())
            {
              try
              {
                running.wait(500);
              }
              catch (InterruptedException ignored)
              {
              }

              int actualDone = initSize - sources.size();
              handle.progress(MessageFormat.format(BUNDLE.getString("LBL_HandleDetails"), actualDone, initSize), actualDone);
            }
          }

          // Fixes die gefailed sind auswerten
          if(!cancellable.isCancelled() && fixesFailed.get() != null && !fixesFailed.get().isEmpty())
            _displayFailedFixes(fixesFailed.get());
        }
      }

      private List<FileObject> _searchFileObject(FileObject pRoot, Predicate<FileObject> pPredicate, AditoHintUtility.CancellableImpl pCancellable)
      {
        ArrayList<FileObject> result = new ArrayList<>();
        for (FileObject child : pRoot.getChildren())
        {
          if(pCancellable.isCancelled())
            return result;

          if(pPredicate.test(child))
            result.add(child);
          result.addAll(_searchFileObject(child, pPredicate, pCancellable));
        }
        return result;
      }

      private void _displayFailedFixes(List<HintFix> pFailedFixes)
      {
        if(!displayFailedFixes)
          return;

        NotifyDescriptor confirmation = new NotifyDescriptor.Message(NbBundle.getMessage(_ProgressRunnable.class, "LBL_FailedFixes", pFailedFixes.size()));
        DialogDisplayer.getDefault().notify(confirmation);
      }
    }
  }

  /**
   * Gibt an, dass ein HintFix von einer Fix-All-Action gefixt werden kann
   */
  public interface IFixAllFixable
  {
    Object getID();

    default boolean implementAndReturn() throws Exception
    {
      return true;
    }
  }

}
