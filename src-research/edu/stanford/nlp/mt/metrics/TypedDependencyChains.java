package edu.stanford.nlp.mt.metrics;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.parser.maltparser.MaltParserInterface;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.Dependencies;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import java.util.function.Predicate;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

public class TypedDependencyChains {
   MaxentTagger tagger;
   MaltParserInterface mpi;
   TokenizerFactory<CoreLabel> ptbtokf;
   Morphology morpha;
   Predicate<String> puncFilter;

   public TypedDependencyChains()  {
     try {
      tagger = new MaxentTagger("/u/cerd/scr/latent.mirror/left3words-wsj-0-18.tagger");
      mpi = new MaltParserInterface("/u/cerd/scr/latent.mirror/engmalt.linear.mco");
      ptbtokf = PTBTokenizer.factory(false, false);
      morpha = new Morphology();
      puncFilter = new PennTreebankLanguagePack().punctuationWordRejectFilter();
     } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
        // throw new RuntimeException(e); herp derp zmert
     }
   }

   public static String wordOnly(String wordIndx) {
     return wordIndx.substring(0,wordIndx.lastIndexOf("-"));
   }

   public Counter<List<TypedDependency>> getChains(String sentence, int maxChain) {
      try {
      List<CoreLabel> words = ptbtokf.getTokenizer(new StringReader(sentence)).tokenize();
      tagger.tagCoreLabels(words);
      for (CoreLabel word : words) {
        String text = word.get(CoreAnnotations.TextAnnotation.class);
        //System.err.println("Token #" + i + ": " + token);
        String posTag = word.get(PartOfSpeechAnnotation.class);
        word.setLemma(morpha.lemma(text, posTag));
      }

      List<TypedDependency> typeDeps;
        typeDeps = mpi.parseToGrammaticalStructure(words).typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL);
      List<TypedDependency> filteredDeps = new ArrayList<TypedDependency>(typeDeps.size());

      for (TypedDependency tdep : typeDeps) {
        if (puncFilter.test(wordOnly(tdep.gov().toString())) && puncFilter.test(wordOnly(tdep.dep().toString()))) {
          filteredDeps.add(tdep);
        }
      }
      //System.out.println(filteredDeps);

      return Dependencies.getTypedDependencyChains(typeDeps, maxChain);
      } catch (Exception e) {
        return null;
      }
   }

   public Counter<List<String>> getNoIndexChains(String sentence, int maxChain, boolean untyped) {

     Counter<List<TypedDependency>> chains = getChains(sentence, maxChain);
     if (chains == null) return null;
     Counter<List<String>> wordDepOnlyStringChains = new ClassicCounter<List<String>>();
     for (List<TypedDependency> chain : chains.keySet()) {
       List<String> deps = new ArrayList<String>(chain.size());
       for (TypedDependency dep : chain) {
         // TODO: double check that this isn't returning (null, null), eg value() is set correctly
         String depString = dep.toString(CoreLabel.OutputFormat.VALUE);
         if (untyped) {
           int firstPar = depString.indexOf("(");
           depString = depString.substring(firstPar);
         }
         deps.add(depString);
       }
       wordDepOnlyStringChains.incrementCount(deps, chains.getCount(chain));
     }
     return wordDepOnlyStringChains;
   }
}
