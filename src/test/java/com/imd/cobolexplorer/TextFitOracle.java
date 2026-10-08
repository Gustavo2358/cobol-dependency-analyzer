package com.imd.cobolexplorer;
import static com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
/** Small example interpreter for handwritten expected results; never used by production. */
final class TextFitOracle {
    static String value(MoveFact move) {
        var recipe=move.textAdjustment().orElseThrow();var source=((LiteralSource)move.source()).logicalValue().orElseThrow().value();
        var output=new StringBuilder();var points=source.codePoints().toArray();
        for(int i=0;i<recipe.receiverExtent();i++)output.appendCodePoint(recipe.rule()==TextAdjustmentRule.ZERO_FILL?'0':i<points.length?points[i]:' ');
        return output.toString();
    }
}
