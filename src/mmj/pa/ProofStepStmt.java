//********************************************************************/
//* Copyright (C) 2005, 2006, 2007, 2008                             */
//* MEL O'CAT  mmj2 (via) planetmath (dot) org                       */
//* License terms: GNU General Public License Version 2              */
//*                or any later version                              */
//********************************************************************/
//*4567890123456 (71-character line to adjust editor window) 23456789*/

/*
 * ProofStepStmt.java  0.07 08/01/2008
 * {@code
 * Version 0.04: 06/01/2007
 *     - Un-nested inner class
 *
 * Version 0.05: 08/01/2007
 *     - added workVarsList to ProofStepStmt
 *     - Work Var Enhancement misc. changes.
 *
 * Nov-01-2007 Version 0.06
 * - add abstract method computeFieldIdCol(int fieldId)
 *   for use in ProofAsstGUI (just in time) cursor
 *   positioning logic.
 * - add proofLevel number field to ProofStepStmt.
 * - add hasMatchingRefLabel()
 * - add localRef to ProofStepStmt
 *
 * Feb-01-2008 Version 0.06
 * - add tmffReformat().
 * - add getStmtDiagnosticInfo()
 * - add updateStmtTextWithWorkVarUpdates()
 * }
 *
 * Aug-01-2008 Version 0.07
 * - modified reviseStepHypRefInStmtText() to use a new
 *   static method reviseStepHypRefInStmtTextArea() so
 *   that the existing stmtText area can be repurposed
 *   to create Metamath statements which use the existing
 *   formula text formatting.
 * - added getRef().
 * - remove stmtHasError() method
 */

package mmj.pa;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import mmj.lang.*;
import mmj.mmio.MMIOError;
import mmj.mmio.Tokenizer;
import mmj.verify.VerifyProofs;

/**
 * ProofStepStmt represents the commonalities of DerivationStep and
 * HypothesisStep.
 */
public abstract class ProofStepStmt extends ProofWorkStmt {

    /**
     * obtained on input, used for carat positioning on errors during input
     * validation.
     */
    protected int formulaStartCharNbr;

    private String step;

    private Stmt ref;

    private String refLabel;

    private Formula formula;

    /**
     * null if no workVars in formula, otherwise contains list of workVars in
     * formula
     */
    List<WorkVar> workVarList;

    /**
     * if parse tree null, unification cannot be attempted for the step or for
     * other steps that refer to this step as an hypothesis.
     */
    public ParseTree formulaParseTree;

    /**
     * Set to true if the formula contains dummy/work variables and a Ref is not
     * entered, signifying that the step is not ready for unification (the
     * standard unification search will not work properly with temp/work
     * variables).
     * <p>
     * NOTE: this is not set to true in the case where
     * {@code deriveStepFormula ==
     * true}; {@code formulaFldIncomplete} is a special situation where
     * unification cannot be attempted, but it is not an "error" by the user,
     * just a delayed entry...and it might occur on a generated proof step.
     */
    boolean formulaFldIncomplete;

    /**
     * this is the step's level in the proof tree, where the 'qed' step has
     * level 0, and any "island" (not used as Hyp) steps also have level 0. The
     * default is 0 and the value is computed during the finale of Proof
     * Worksheet parsing, and then when new steps (if any) are generated by the
     * Derive feature. The proofLevel number is used for proof formatting
     * indentation by TMFF.
     */
    int proofLevel;

    /** For the automatic transformations feature */
    private ProofTransformations.Transformation canonicalTransformation;
    /** For the automatic transformations feature */
    private boolean sameCanonicalForm = false;

    /**
     * Default Constructor.
     * 
     * @param w the owning ProofWorksheet
     */
    public ProofStepStmt(final ProofWorksheet w) {
        super(w);
    }

    /**
     * Constructor for incomplete ProofStepStmt destined only for output to the
     * GUI.
     * <p>
     * Creates "incomplete" ProofStepStmt which is destined only for output to
     * the GUI, hence, the object references, etc. are not loaded. After display
     * to the GUI this worksheet disappears -- recreated via "load" each time
     * the user selects "StartUnification".
     * 
     * @param w The owning ProofWorksheet
     * @param step step number of the ProofStepStmt
     * @param refLabel Ref label of the ProofStepStmt
     * @param formula the proof step formula
     * @param setCaret true means position caret of TextArea to this statement.
     */
    public ProofStepStmt(final ProofWorksheet w, final String step,
        final String refLabel, final Formula formula, final boolean setCaret)
    {

        super(w);
        this.step = step;
        this.refLabel = refLabel;
        this.formula = formula;

        if (setCaret)
            w.proofCursor.setCursorAtProofWorkStmt(this,
                PaConstants.FIELD_ID_REF);

    }

    public String getStep() {
        return step;
    }

    public void setStep(final String step) {
        this.step = step;
    }

    /**
     * Loads the proofLevel number with the input proofLevel but only if the
     * existing proofLevel is zero.
     * <p>
     * Note: does not update a non-zero proofLevel because a step can be used
     * more than once as an hypothesis, so different level numbers could result.
     * 
     * @param proofLevel value to store if existing level is zero.
     */
    public void loadProofLevel(final int proofLevel) {
        if (this.proofLevel == 0)
            this.proofLevel = proofLevel;
    }

    /**
     * Computes column number in the proof step text of the input field id.
     * 
     * @param fieldId value identify ProofWorkStmt field for cursor positioning,
     *            as defined in PaConstants.FIELD_ID_*.
     * @return column of input fieldId or default value of 1 if there is an
     *         error.
     */
    @Override
    public int computeFieldIdCol(final int fieldId) {
        int outCol = 1;
        if (fieldId == PaConstants.FIELD_ID_REF)
            outCol = computeFieldIdColRef();
        return outCol;
    }

    /** Reformats Derivation Step using TMFF. */
    @Override
    public void tmffReformat() {}

    /**
     * Function used for cursor positioning.
     * <p>
     * The function looks for the Ref in the 2nd colon in the
     * ProofWorkStmt.stmtText and sets the output column to the column *after*
     * the 2nd colon -- but returns 1 if whitespace is found before the 2nd
     * colon in the stmtText.
     * 
     * @return column of Ref subfield in proof step of 1 if there is an error.
     */
    public int computeFieldIdColRef() {
        int outCol = 1;

        char c;
        int nbrColons = 0;
        int i = 0;
        while (i < stmtText.length()) {
            c = stmtText.charAt(i++);
            if (c == PaConstants.FIELD_DELIMITER_COLON) {
                if (nbrColons > 0) {
                    outCol = i + 1; // +1 to *next* column!
                    break;
                }
                nbrColons++;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\n')
                break;
            continue;
        }

        return outCol;
    }

    /**
     * Updates the ProofStepStmt ParseTree, resetting maxDepth and levelOneTwo
     * data.
     * 
     * @param parseTree the new ParseTree for the step.
     */
    public void updateFormulaParseTree(final ParseTree parseTree) {
        if (parseTree != null) {
            parseTree.resetMaxDepth();
            parseTree.resetLevelOneTwo();
        }
        formulaParseTree = parseTree;
    }

    public void accumSetOfWorkVarsUsed(final Set<WorkVar> set) {
        if (workVarList == null)
            return;
        for (final WorkVar var : workVarList)
            set.add(var);
    }

    /**
     * Updates the workVarList for the ProofStepStmt and if the revised
     * workVarList is null turns off the formulaFldIncomplete flag.
     * 
     * @param workVarList List of WorkVar listing the WorkVar used in the step
     *            formula.
     */
    public void updateWorkVarList(final List<WorkVar> workVarList) {
        if (workVarList != null && workVarList.isEmpty())
            this.workVarList = null;
        else
            this.workVarList = workVarList;

        if (this.workVarList == null)
            formulaFldIncomplete = false;
    }

    /**
     * Updates stmtText in place using substitution values assigned to work
     * variables used in the step.
     * <p>
     * NOTE: This is a dirty little hack :-)
     * </p
     * ?
     * 
     * @param verifyProofs instance of VerifyProofs for use converting an RPN
     *            list to a formula.
     * @return true if stmtText updated successfully.
     */
    public boolean updateStmtTextWithWorkVarUpdates(
        final VerifyProofs verifyProofs)
    {

        // 0) BSTF - Better Safe Than Sorry
        if (stmtText.length() == 0 || workVarList == null)
            return false; // no changes to make, AsIs ok.

        // 1) Build from/to search strings for updating the
        // formula portion of stmtText.

        final String[] wvArray = new String[workVarList.size()];

        final String[] wvSubstStringArray = new String[workVarList.size()];

        WorkVar workVar;
        ParseNode substNode;
        Formula formula;
        int wvCnt = 0;
        for (int i = 0; i < workVarList.size(); i++) {
            workVar = workVarList.get(i);
            substNode = ((WorkVarHyp)workVar.getActiveVarHyp()).paSubst;
            if (substNode == null)
                continue;
            wvArray[wvCnt] = workVar.getId();
            formula = verifyProofs.convertRPNToFormula(
                substNode.convertToRPN(true), "");
            wvSubstStringArray[wvCnt] = formula.exprToString();
            wvCnt++;
        }

        if (wvCnt == 0)
            return true; // no changes to make, AsIs ok.

        // 2) Create a mmj.mmio.Tokenizer to parse the
        // stmtText string. Then bypass the first
        // token (the Step/Hyp/Ref field) and any
        // white space: the 2nd token will be the
        // start of the formula. Apply updates!

        try {
            final StringBuilder outStmtText = new StringBuilder(
                stmtText.length() + 10);

            int outStmtTextOffset = 0;
            int len = 0;

            final Tokenizer tokenizer = new Tokenizer(new StringReader(
                stmtText.toString()), "");

            // bypass 1st token and surrounding whitespace
            len = tokenizer.getWhiteSpace(outStmtText, outStmtTextOffset);
            if (len < 0)
                return false; // bogus stmtText?
            outStmtTextOffset += len;

            len = tokenizer.getToken(outStmtText, outStmtTextOffset);
            if (len < 0)
                return false; // bogus stmtText?
            outStmtTextOffset += len;

            len = tokenizer.getWhiteSpace(outStmtText, outStmtTextOffset);
            if (len < 0)
                return false; // bogus stmtText?
            outStmtTextOffset += len;

            len = tokenizer.getToken(outStmtText, outStmtTextOffset);
            if (len < 0)
                return false; // bogus stmtText?

            // ok, now we have the 2nd token in hand! proceed..
            String token;
            int end;
            while (true) {
                end = outStmtTextOffset + len;
                token = outStmtText.substring(outStmtTextOffset, end);
                for (int i = 0; i < wvCnt; i++) {
                    if (!token.equals(wvArray[i]))
                        continue;
                    outStmtText.replace(outStmtTextOffset, end,
                        wvSubstStringArray[i]);
                    end = outStmtTextOffset + wvSubstStringArray[i].length();
                    break;
                }
                outStmtTextOffset = end;

                len = tokenizer.getWhiteSpace(outStmtText, outStmtTextOffset);
                if (len < 0)
                    break;
                outStmtTextOffset += len;
                len = tokenizer.getToken(outStmtText, outStmtTextOffset);
                if (len < 0)
                    break;
            }

            stmtText = outStmtText;

            return true;

        } catch (final IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (final MMIOError e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Loads stmtText and formula fields with the input tokens.
     * <p>
     * The formula is loaded and validated before Step/Hyp/Ref so that the
     * formula symbols can be validated as they are read in. We return to
     * validate Step/Hyp/Ref after doing the formula. The big problem being
     * solved here is positioning the caret as accurately as possible to the
     * first reported error on the line.
     * 
     * @param workVarsOk indicator to allow/disallow WorkVar syms in this
     *            formula.
     * @return String showing the proof text statement
     * @throws IOException if an error occurred
     * @throws MMIOError if an error occurred
     * @throws ProofAsstException if an error occurred
     */
    public String loadStmtTextWithFormula(final boolean workVarsOk)
        throws IOException, MMIOError, ProofAsstException
    {
        final int currLineNbr = (int)w.proofTextTokenizer.getCurrentLineNbr();

        stmtText = new StringBuilder();

        // input "" because we're skipping step/hyp/ref token
        // for now...and inserting them in stmtText later.
        String nextT = loadStmtTextGetOptionalToken("");

        final int colNbr = (int)w.proofTextTokenizer.getCurrentColumnNbr();

        // formula considered optional with new "Derive"
        // feature -- callers need to interrogate the step's
        // formula field to see if it was input.
        if (nextT.length() == 0 || nextT.length() == colNbr)
            return nextT;

        // save char nbr for later use positioning caret
        formulaStartCharNbr = (int)w.proofTextTokenizer.getCurrentCharNbr();

        if (!nextT.equals(w.getProvableLogicStmtTyp().getId()))
            w.triggerLoadStructureException(nextT.length(),
                PaConstants.ERRMSG_BAD_TYP_CD2, w.getErrorLabelIfPossible(),
                step, nextT, w.getProvableLogicStmtTyp().getId());

        final List<Sym> symList = new ArrayList<Sym>();
        symList.add(w.getProvableLogicStmtTyp());

        nextT = loadStmtTextGetRequiredToken(nextT);
        Sym sym;
        while (true) {
            sym = w.comboVarMap.get(nextT);
            if (sym == null) {
                sym = w.logicalSystem.getSymTbl().get(nextT);
                if (sym == null) {
                    if ((sym = w.proofAsstPreferences.getWorkVarManager()
                        .alloc(nextT)) != null)
                    {
                        if (!workVarsOk)
                            w.triggerLoadStructureException(nextT.length(),
                                PaConstants.ERRMSG_WV_LOC_ERR,
                                w.getErrorLabelIfPossible(), step, nextT);
                        w.hasWorkVarsOrDerives = true;
                        accumWorkVarList((WorkVar)sym);
                    }
                    else
                        w.triggerLoadStructureException(nextT.length(),
                            PaConstants.ERRMSG_SYM_NOTFND,
                            w.getErrorLabelIfPossible(), step, nextT);
                }
                else if (!(sym instanceof Cnst))
                    w.triggerLoadStructureException(nextT.length(),
                        PaConstants.ERRMSG_VAR_SCOPE,
                        w.getErrorLabelIfPossible(), step, nextT);
            }
            if (sym.getSeq() >= w.getMaxSeq())
                w.triggerLoadStructureException(nextT.length(),
                    PaConstants.ERRMSG_SYM_MAXSEQ, w.getErrorLabelIfPossible(),
                    step, nextT);

            symList.add(sym);

            nextT = loadStmtTextGetOptionalToken(nextT);

            if (nextT.length() == 0
                || nextT.length() == w.proofTextTokenizer.getCurrentColumnNbr())
                break;
        }

        formula = new Formula(symList);

        // keep track of step line number here because
        // we are processing the raw input tokens here.
        updateLineCntUsingTokenizer(currLineNbr, nextT);

        return nextT;
    }

    /**
     * Function to determine whether the ProofWorkStmt Ref Label matches the
     * input string (always false in base class.)
     * 
     * @param ref string to compare to ProofWorkStmt refLabel string.
     * @return true if ProofStepStmt Ref label matches the input string.
     */
    @Override
    public boolean hasMatchingRefLabel(final String ref) {
        return refLabel != null && refLabel.equals(ref);
    }

    /**
     * Compares input Step number to this step.
     * 
     * @param newStepNbr step number to compare
     * @return true if equal, false if not equal.
     */
    @Override
    public boolean hasMatchingStepNbr(final String newStepNbr) {
        return step.equals(newStepNbr);
    }

    /**
     * Sets the Ref Stmt for the step.
     * 
     * @param ref Stmt in LogicalSystem
     */
    public void setRef(final Stmt ref) {
        this.ref = ref;
    }

    /**
     * Gets the Ref Stmt for the step.
     * 
     * @return Ref stmt for the ProofStepStmt;
     */
    public Stmt getRef() {
        return ref;
    }

    /**
     * Sets the Ref label for the step.
     * <p>
     * Note: refLabel is input first and Ref is derived, but for new Theorems,
     * Ref may not exist, which is why we carry both around at all times.
     * 
     * @param refLabel Ref label for the step.
     */
    public void setRefLabel(final String refLabel) {
        this.refLabel = refLabel;
    }

    /**
     * Gets the Ref label for the step.
     * 
     * @return Ref label for the step.
     */
    public String getRefLabel() {
        return refLabel;
    }

    @Override
    public String getStmtDiagnosticInfo() {
        return this.getClass().getName() + " " + step;
    }

    /**
     * Updates the first token of the text area.
     * <p>
     * Assumes text already contains StepHypRef token.
     * <p>
     * Objective is to avoid changing the position of the other tokens within
     * the text area -- as much as possible.
     * 
     * @param textArea step stmtText area or similar text area.
     * @param newTextPrefix revised first token of text area.
     */
    public static void reviseStepHypRefInStmtTextArea(
        final StringBuilder textArea, final StringBuilder newTextPrefix)
    {

        int pos = 0;
        char c;
        // bypass non-whitespace at start of line
        while (pos < textArea.length()) {
            c = textArea.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n')
                break;
            pos++;
        }
        while (pos < textArea.length()) {
            c = textArea.charAt(pos);
            if (c == ' ' || c == '\t')
                pos++;
            else
                break;
        }

        newTextPrefix.append(' ');

        int padding = pos - newTextPrefix.length();
        if (padding > 0) {
            do
                newTextPrefix.append(' ');
            while (--padding > 0);
            pos = newTextPrefix.length();
        }
        textArea.replace(0, pos, newTextPrefix.toString());
    }

    /**
     * Renders formula into stepHypRef string buffer and returns the number of
     * lines in the formula. (This assumes that stepHypRef requires only one
     * line, which, theoretically could be wrong!)
     * 
     * @param stepHypRef the string buffer
     * @param formula the formula
     * @param parseTree of the Formula to be rendered. If left null, the formula
     *            will be output in unformatted
     * @return number of lines in the formula
     */
    protected int loadStmtText(final StringBuilder stepHypRef,
        final Formula formula, final ParseTree parseTree)
    {

        stepHypRef.append(' ');

        w.tmffSP.setSB(stepHypRef);

        w.tmffSP.setPrevColNbr(stepHypRef.length());

        final int nbrLines = w.tmffPreferences.renderFormula(w.tmffSP,
            parseTree, formula, proofLevel);

        stepHypRef.append("\n");

        return nbrLines;
    }

    protected void accumWorkVarList(final WorkVar workVar) {
        if (workVarList == null) {
            workVarList = new ArrayList<WorkVar>(3); // arbitrary guess...
            workVarList.add(workVar);
        }
        else if (!workVarList.contains(workVar))
            workVarList.add(workVar);
    }

    // on existing formulas we retrieve parse tree from Stmt
    protected void getNewFormulaStepParseTree() throws ProofAsstException {
        formulaParseTree = w.grammar.parseFormulaWithoutSafetyNet(formula,
            w.comboFrame.hypArray, // is array, confusingly...
            w.getMaxSeq());
        if (formulaParseTree == null)
            w.triggerLoadStructureException(
                (int)w.proofTextTokenizer.getCurrentCharNbr() + 1
                    - formulaStartCharNbr, PaConstants.ERRMSG_PARSE_ERR,
                w.getErrorLabelIfPossible(), step);
    }

    // assumes formula already loaded (first), and possibly
    // preceded by space characters.
    protected void loadStepHypRefIntoStmtText(final int origStepHypRefLength,
        final StringBuilder stepHypRef)
    {

        int diff = stepHypRef.length() - origStepHypRefLength;
        while (diff < 0) {
            stepHypRef.append(' ');
            diff++;
        }
        if (diff == 0) {
            stmtText.insert(0, stepHypRef);
            return;
        }

        int nbrSpaces = 0;
        char c;
        while (nbrSpaces < stmtText.length()) {
            c = stmtText.charAt(nbrSpaces);
            if (c == ' ' || c == '\t')
                nbrSpaces++;
            else
                break;
        }

        if (nbrSpaces == 0) {
            stepHypRef.append(' ');
            stmtText.insert(0, stepHypRef);
            return;
        }

        if (diff < nbrSpaces)
            stmtText.replace(0, diff, stepHypRef.toString());
        else
            stmtText.replace(0, nbrSpaces - 1, stepHypRef.toString());
    }

    /**
     * Updates the Step/Hyp/Ref field in the statement text area of the proof
     * step.
     * <p>
     * This is needed because Unify can alter Hyp and Ref.
     */
    public abstract void reloadStepHypRefInStmtText();

    // assumes StmtText already contains StepHypRef
    protected void reviseStepHypRefInStmtText(final StringBuilder newStepHypRef)
    {
        ProofStepStmt.reviseStepHypRefInStmtTextArea(stmtText, newStepHypRef);
    }

    /**
     * Renumbers step numbers using a HashMap containing old and new step number
     * pairs.
     * 
     * @param renumberMap contains key/value pairs defining newly assigned step
     *            numbers.
     */
    public abstract void renum(Map<String, String> renumberMap);

    public ProofStepStmt getLocalRef() {
        return null;
    }

    public Formula getFormula() {
        return formula;
    }

    public void setFormula(final Formula formula) {
        this.formula = formula;
    }

    public ProofTransformations.Transformation getCanonicalTransformation() {
        return canonicalTransformation;
    }

    public void setCanonicalTransformation(
        final ProofTransformations.Transformation canonicalTransformation)
    {
        if (canonicalTransformation == null)
            sameCanonicalForm = true;
        else
            sameCanonicalForm = false;
        this.canonicalTransformation = canonicalTransformation;
    }

    public boolean isSameCanonicalForm() {
        return sameCanonicalForm;
    }

    public ParseNode getCanonicalForm() {
        if (sameCanonicalForm)
            return formulaParseTree.getRoot();
        else
            return canonicalTransformation.canonResult;
    }
}
