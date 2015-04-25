package org.nerdpower.tabula;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDFont;

@SuppressWarnings("serial")
public class TextElement extends Rectangle implements HasText {

    private final String text;
    private final PDFont font;
    private float fontSize;
    private float widthOfSpace, dir;
    private static final float AVERAGE_CHAR_TOLERANCE = 0.3f;

    public TextElement(float y, float x, float width, float height,
            PDFont font, float fontSize, String c, float widthOfSpace) {
        super();
        this.setRect(x, y, width, height);
        this.text = c;
        this.widthOfSpace = widthOfSpace;
        this.fontSize = fontSize;
        this.font = font;
    } 

    public TextElement(float y, float x, float width, float height,
            PDFont font, float fontSize, String c, float widthOfSpace, float dir) {
        super();
        this.setRect(x, y, width, height);
        this.text = c;
        this.widthOfSpace = widthOfSpace;
        this.fontSize = fontSize;
        this.font = font;
        this.dir = dir;
    }

    public String getText() {
        return text;
    }

    public float getDirection() {
        return dir;
    }

    public float getWidthOfSpace() {
        return widthOfSpace;
    }

    public PDFont getFont() {
        return font;
    }

    public float getFontSize() {
        return fontSize;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String s = super.toString();
        sb.append(s.substring(0, s.length() - 1));
        sb.append(String.format(",text=\"%s\"]", this.getText()));
        return sb.toString();
    }
    
    public static List<TextChunk> mergeWords(List<TextElement> textElements) {
        return mergeWords(textElements, new ArrayList<Ruling>());
    }
    
    /**
     * heuristically merge a list of TextElement into a list of TextChunk
     * ported from from PDFBox's PDFTextStripper.writePage, with modifications.
     * Here be dragons
     * 
     * @param textElements
     * @param verticalRulings
     * @return
     */
    public static List<TextChunk> mergeWords(List<TextElement> textElements, List<Ruling> verticalRulings) {
        
        List<TextChunk> textChunks = new ArrayList<TextChunk>();
        
        if (textElements.isEmpty()) {
            return textChunks;
        }
        
        textChunks.add(new TextChunk(textElements.remove(0)));
        TextChunk firstTC = textChunks.get(0); 

        List<java.lang.Float> charWidthsSoFar = new ArrayList<java.lang.Float>();
        List<java.lang.Float> wordSpacingsSoFar = new ArrayList<java.lang.Float>();
        float endOfLastTextX = (float) firstTC.getRight();
        float maxYForLine = (float) firstTC.getBottom();
        float maxHeightForLine = (float) firstTC.getHeight();
        float minYTopForLine = (float) firstTC.getTop();
        float wordSpacing, deltaSpace, averageCharWidth, deltaCharWidth;
        float expectedStartOfNextWordX, dist;
        TextElement sp, prevChar;
        TextChunk currentChunk;
        boolean sameLine, acrossVerticalRuling;
        
        for (TextElement chr : textElements) {
            currentChunk = textChunks.get(textChunks.size() - 1);
            prevChar = currentChunk.textElements.get(currentChunk.textElements.size() - 1);
            
            // if same char AND overlapped, skip
            if ((chr.getText().equals(prevChar.getText())) && (prevChar.overlapRatio(chr) > 0.5)) {
                continue;
            }
            
            // if chr is a space that overlaps with prevChar, skip
            if (chr.getText().equals(" ") && Utils.feq(prevChar.getLeft(), chr.getLeft()) && Utils.feq(prevChar.getTop(), chr.getTop())) {
                continue;
            }
            
            // Resets the character/spacing widths (used for averages) when we see a change in font
            // or a change in the font size
            if ((chr.getFont() != prevChar.getFont()) || !Utils.feq(chr.getFontSize(), prevChar.getFontSize())) {
              charWidthsSoFar = new ArrayList<java.lang.Float>();
              wordSpacingsSoFar = new ArrayList<java.lang.Float>();
            }

            // is there any vertical ruling that goes across chr and prevChar?
            acrossVerticalRuling = false;
            for (Ruling r: verticalRulings) {
                if (    
                        (verticallyOverlapsRuling(prevChar, r) && verticallyOverlapsRuling(chr, r)) &&
                        (prevChar.x < r.getPosition() && chr.x > r.getPosition()) || (prevChar.x > r.getPosition() && chr.x < r.getPosition())
                    ) {
                    acrossVerticalRuling = true;
                    break;
                }
            } 
            
            // Estimate the expected width of the space based on the
            // average width of the space character with some margin.
            wordSpacing = chr.getWidthOfSpace();
            deltaSpace = 0;
            if (java.lang.Float.isNaN(wordSpacing) || wordSpacing == 0) {
                deltaSpace = java.lang.Float.MAX_VALUE;
            }
            else if (wordSpacingsSoFar.size() == 0) {
                deltaSpace = wordSpacing * 0.5f; // 0.5 == spacing tolerance
            }
            else {
                float sumWordSpacings = 0.0f;
                for(float pastWordSpacing : wordSpacingsSoFar){
                    sumWordSpacings += pastWordSpacing;
                }
                deltaSpace = (sumWordSpacings / wordSpacingsSoFar.size() ) * 0.5f;
            }

            wordSpacingsSoFar.add((float) wordSpacing);
            charWidthsSoFar.add((float) chr.getWidth());
            
            // Estimate the expected width of the space based on the
            // average character width with some margin. Based on experiments we also found that 
            // .3 worked well.
            float sumCharWidths = 0.0f;
            for(float pastCharWidth : charWidthsSoFar){
                sumCharWidths += pastCharWidth;
            }
            averageCharWidth = (sumCharWidths / charWidthsSoFar.size() ) * 0.5f;
            deltaCharWidth = averageCharWidth * AVERAGE_CHAR_TOLERANCE;
            
            // Compares the values obtained by the average method and the wordSpacing method and picks
            // the smaller number.
            expectedStartOfNextWordX = -java.lang.Float.MAX_VALUE;
            
            if (endOfLastTextX != -1) {
                expectedStartOfNextWordX = endOfLastTextX + Math.min(deltaCharWidth, deltaSpace);
            }
            
            // new line?
            sameLine = true;
            if (!Utils.overlap((float) chr.getBottom(), chr.height, maxYForLine, maxHeightForLine)) {
                endOfLastTextX = -1;
                expectedStartOfNextWordX = -java.lang.Float.MAX_VALUE;
                maxYForLine = -java.lang.Float.MAX_VALUE;
                maxHeightForLine = -1;
                minYTopForLine = java.lang.Float.MAX_VALUE;
                sameLine = false;
            }
            
            // characters tend to be ordered by their left location
            // in determining whether to add a space, we need to know the distance
            // between the current character's left and the nearest character's 
            // right. The nearest character may not be the previous character, so we
            // need to keep track of the character with the greatest right x-axis
            // location -- that's endOfLastTextX
            // (in some fonts, one character may be completely "on top of"
            // another character, with the wider character starting to the left and 
            // ending to the right of the narrower character,  e.g. ANSI 
            // representations of some South Asian languages, see 
            // https://github.com/tabulapdf/tabula/issues/303)
            endOfLastTextX = Math.max((float) chr.getRight(), endOfLastTextX);

            // should we add a space?
            if (!acrossVerticalRuling &&
                sameLine &&
                expectedStartOfNextWordX < chr.getLeft() && 
                !prevChar.getText().endsWith(" ")) {
                
                sp = new TextElement((float) prevChar.getTop(),
                        (float) prevChar.getLeft(),
                        (float) (expectedStartOfNextWordX - prevChar.getLeft()),
                        (float) prevChar.getHeight(),
                        prevChar.getFont(),
                        prevChar.getFontSize(),
                        " ",
                        prevChar.getWidthOfSpace());
                
                currentChunk.add(sp);
            }
            else {
                sp = null;
            }
            
            maxYForLine = (float) Math.max(chr.getBottom(), maxYForLine);
            maxHeightForLine = (float) Math.max(maxHeightForLine, chr.getHeight());
            minYTopForLine = (float) Math.min(minYTopForLine, chr.getTop());

            dist = (float) (chr.getLeft() - (sp != null ? sp.getRight() : prevChar.getRight()));

            if (!acrossVerticalRuling &&
                sameLine &&
                (dist < 0 ? currentChunk.verticallyOverlaps(chr) : dist < wordSpacing)) {
                currentChunk.add(chr);
            }
            else { // create a new chunk
               textChunks.add(new TextChunk(chr));
            }
        }

        // sort each textChunk's textElements by their center
        for (TextChunk textChunk : textChunks) {
            textChunk.sortByCenters();
        }

        return textChunks;
    }
    
    private static boolean verticallyOverlapsRuling(TextElement te, Ruling r) {
        // Utils.overlap(prevChar.getTop(), prevChar.getHeight(), r.getY1(), r.getY2() - r.getY1())
        return Math.max(0, Math.min(te.getBottom(), r.getY2()) - Math.max(te.getTop(), r.getY1())) > 0;
    }
    
}
