package edu.stanford.nlp.mt.decoder.h;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.DTUOption;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class DTUIsolatedPhraseForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

  private static final double MINUS_INF = -10000.0;

  public static final String DEBUG_PROPERTY = "ipfcHeuristicDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  final IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer;
	final Scorer<FV> scorer;

  @Override
	public Object clone() throws CloneNotSupportedException {
    return super.clone();
	}
	
	/**
	 * 
	 */
	public DTUIsolatedPhraseForeignCoverageHeuristic(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
    System.err.println("Heuristic: "+getClass());
  }
	
	@Override
	public double getHeuristicDelta(Hypothesis<TK, FV> newHypothesis, CoverageSet newCoverage) {
    
    double oldH = newHypothesis.preceedingHyp.h;
		double newH = 0.0;


    CoverageSet coverage = newHypothesis.foreignCoverage;
		int startEdge = coverage.nextClearBit(0);

    if(Double.isNaN(oldH)) {
      System.err.printf("getHeuristicDelta:\n");
      System.err.printf("coverage: %s\n", newHypothesis.foreignCoverage);
      System.err.println("old H: "+oldH);
      throw new RuntimeException();
    }

    int foreignSize = newHypothesis.foreignSequence.size();
		for (int endEdge; startEdge < foreignSize; startEdge = coverage.nextClearBit(endEdge)) {
			endEdge = coverage.nextSetBit(startEdge);
			
			if (endEdge == -1) {
				endEdge = newHypothesis.foreignSequence.size();
			}
      
      double localH = hSpanScores.getScore(startEdge, endEdge-1);

      if (Double.isNaN(localH)) {
        System.err.printf("Bad retrieved score for %d:%d ==> %f\n", startEdge, endEdge-1, localH);
        throw new RuntimeException();
      }

      newH += localH;
      if (Double.isNaN(newH)) {
        System.err.printf("Bad total retrieved score for %d:%d ==> %f (localH=%f)\n", startEdge, endEdge-1, newH, localH);
        throw new RuntimeException();
      }
		}
    if((Double.isInfinite(newH) || newH == MINUS_INF) && (Double.isInfinite(oldH) || oldH == MINUS_INF))
      return 0.0;
    double delta = newH - oldH;
    ErasureUtils.noop(delta);
    //if(Double.isInfinite(delta) || Double.isNaN(delta)) {
      //System.err.println("h delta is not valid: "+delta);
      //System.err.println("newH: "+newH);
      //System.err.println("oldH: "+oldH);
    //}
    return delta;
  }

	private SpanScores hSpanScores;
	
	@Override
	public double getInitialHeuristic(Sequence<TK> foreignSequence,
			List<List<ConcreteTranslationOption<TK>>> options, int translationId) {
		
		int foreignSequenceSize = foreignSequence.size();
		
		SpanScores viterbiSpanScores = new SpanScores(foreignSequenceSize);
		
		if (DEBUG) {
			System.err.println("IsolatedPhraseForeignCoverageHeuristic");
			System.err.printf("Foreign Sentence: %s\n", foreignSequence);
			
			System.err.println("Initial Spans from PhraseTable");
			System.err.println("------------------------------");
		}
		
		// initialize viterbiSpanScores
    System.err.println("Lists of options: "+options.size());
    assert(options.size() == 1 || options.size() == 2); // options[0]: phrases without gaps; options[1]: phrases with gaps
    for (int i=0; i<options.size(); ++i) {
      for (ConcreteTranslationOption<TK> option : options.get(i)) {
        if (option.abstractOption instanceof DTUOption) {
          //System.err.println("future cost: skipping: "+option.abstractOption);
          continue;
        }
        Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence, option, translationId);
        List<FeatureValue<FV>> phraseFeatures = phraseFeaturizer.phraseListFeaturize(f);
        double score = scorer.getIncrementalScore(phraseFeatures), childScore = 0.0;
        final int terminalPos;
        if(i==0) {
          terminalPos = option.foreignPos + option.abstractOption.foreign.size()-1;
          if (score > viterbiSpanScores.getScore(option.foreignPos, terminalPos)) {
            viterbiSpanScores.setScore(option.foreignPos, terminalPos, score);
            if (Double.isNaN(score)) {
              System.err.printf("Bad Viterbi score: score[%d,%d]=%.3f\n", option.foreignPos, terminalPos, score);
              throw new RuntimeException();
            }
          }
        } else {
          terminalPos = option.foreignCoverage.length()-1;
          // Find all gaps:
          CoverageSet cs = option.foreignCoverage;
          //System.err.println("coverage set: "+cs);
          int startIdx, endIdx = 0;
          childScore = 0.0;
          while (true) {
            startIdx = cs.nextClearBit(cs.nextSetBit(endIdx));
            endIdx = cs.nextSetBit(startIdx)-1;
            if(endIdx < 0)
              break;
            childScore += viterbiSpanScores.getScore(startIdx, endIdx);
            //System.err.printf("range: %d-%d\n", startIdx, endIdx);
          }
          double totalScore = score + childScore;
          double oldScore = viterbiSpanScores.getScore(option.foreignPos, terminalPos);
          if (totalScore > oldScore) {
            viterbiSpanScores.setScore(option.foreignPos, terminalPos, totalScore);
            if (Double.isNaN(totalScore)) {
              System.err.printf("Bad Viterbi score[%d,%d]: score=%.3f childScore=%.3f\n", option.foreignPos, terminalPos, score, childScore);
              throw new RuntimeException();
            }
            if (DEBUG)
              System.err.printf("Improved with gaps: %.3f -> %.3f\n", oldScore, totalScore);

          }
        }
        if (DEBUG) {
          System.err.printf("\t%d:%d:%d %s->%s score: %.3f %.3f\n", option.foreignPos, terminalPos, i, option.abstractOption.foreign, option.abstractOption.translation, score, childScore);
          System.err.printf("\t\tFeatures: %s\n", phraseFeatures);
        }
      }

      if (DEBUG) {
        System.err.println("Initial Minimums");
        System.err.println("------------------------------");

        for (int startPos = 0; startPos < foreignSequenceSize; startPos++) {
          for (int endPos = startPos; endPos < foreignSequenceSize; endPos++) {
            System.err.printf("\t%d:%d score: %f\n", startPos, endPos, viterbiSpanScores.getScore(startPos, endPos));
          }
        }
      }



      if (DEBUG) {
        System.err.println();
        System.err.println("Merging span scores");
        System.err.println("-------------------");
      }

      // Viterbi combination of spans
      for (int spanSize = 2; spanSize <= foreignSequenceSize; spanSize++) {
        if (DEBUG) {
          System.err.printf("\n* Merging span size: %d\n", spanSize);
        }
        for (int startPos = 0; startPos <= foreignSequenceSize-spanSize; startPos++) {
          int terminalPos = startPos + spanSize-1;
          double bestScore = viterbiSpanScores.getScore(startPos, terminalPos);
          for (int centerEdge = startPos+1; centerEdge <= terminalPos; centerEdge++) {
            double combinedScore = viterbiSpanScores.getScore(startPos, centerEdge-1) +
                         viterbiSpanScores.getScore(centerEdge, terminalPos);
            if (combinedScore > bestScore) {
              if (DEBUG) {
                System.err.printf("\t%d:%d updating to %.3f from %.3f\n", startPos, terminalPos, combinedScore, bestScore);
              }
              bestScore = combinedScore;
            }
          }
          viterbiSpanScores.setScore(startPos, terminalPos, bestScore);
        }
      }
    }

    if (DEBUG) {
			System.err.println();
			System.err.println("Final Scores");
			System.err.println("------------");
			for (int startEdge = 0; startEdge < foreignSequenceSize; startEdge++) {
				for (int terminalEdge = startEdge; terminalEdge < foreignSequenceSize; terminalEdge++) {
					System.err.printf("\t%d:%d score: %.3f\n", startEdge, terminalEdge, viterbiSpanScores.getScore(startEdge, terminalEdge));
				}
			}
		}
		
		hSpanScores = viterbiSpanScores;
		
		double hCompleteSequence = hSpanScores.getScore(0, foreignSequenceSize-1); 
		if (DEBUG) {
			System.err.println("Done IsolatedForeignCoverageHeuristic");
		}

    if(Double.isInfinite(hCompleteSequence) || Double.isNaN(hCompleteSequence))
      return MINUS_INF;
    return hCompleteSequence;
	}
		
	private static class SpanScores {
		final double[] spanValues;
		final int terminalPositions;
		public SpanScores(int length) {
			terminalPositions = length+1;			
			spanValues = new double[terminalPositions*terminalPositions];
			Arrays.fill(spanValues, Double.NEGATIVE_INFINITY);
		}
		
		public double getScore(int startPosition, int endPosition) {
			return spanValues[startPosition*terminalPositions + endPosition];
		}
		
		public void setScore(int startPosition, int endPosition, double score) {
			spanValues[startPosition*terminalPositions + endPosition] = score;
		}
	}
}
