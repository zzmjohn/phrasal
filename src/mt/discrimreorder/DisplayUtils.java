package mt.discrimreorder;

import edu.stanford.nlp.stats.*;

import java.io.*;
import java.util.*;

import mt.train.*;

/**
 * This class collects utils that can display information in
 * {@link AlignmentMatrix}.
 *
 * Some code are the same as in {@link mt.train.transtb.AlignmentUtils}
 * 
 * This class also collects other displaying utils, such as
 * outputing the confusion matrix.
 * @author Pi-Chuan Chang
 */

public class DisplayUtils {
  public static void printExamples(TrainingExamples examples) {
    printExamples(examples, new PrintWriter(System.out, true));
  }

  public static void printExamples(TrainingExamples examples, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr>");
    pw.println("<td> i </td>");
    pw.println("<td> j </td>");
    pw.println("<td> j' </td>");
    pw.println("<td> class </td>");
    pw.println("</tr>");
    for(TrainingExample example : examples.examples) {
      pw.println("<tr>");
      pw.printf("<td> %d </td>\n", example.tgt_i);
      pw.printf("<td> %d </td>\n", example.src_j);
      pw.printf("<td> %d </td>\n", example.src_jprime);
      pw.printf("<td> %s </td>\n", example.type);
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  public static void printAlignmentMatrixHeader() {
    printAlignmentMatrixHeader(new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrixHeader(PrintWriter pw) {
    pw.println("<br></body></html>");
    pw.println("<html><head><style type=\"text/css\"> table {border-collapse: collapse;} td { padding: 4px; border: 1px solid black } </style>");
  }

  public static void printAlignmentMatrixBottom() {
    printAlignmentMatrixBottom(new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrixBottom(PrintWriter pw) {
    pw.println("<br></body></html>");
  }

  public static void printAlignmentMatrix(AlignmentMatrix am) {
    printAlignmentMatrix(am, new PrintWriter(System.out, true));
  }

  public static void printAlignmentMatrix(AlignmentMatrix am, PrintWriter pw) {
    pw.println("<table>");
    pw.println("<tr><td></td>");
    for(int i = 0; i < am.f.length; i++) {
      pw.printf("<td>(%d) %s</td>\n", i, escapeHtml(am.f[i]));
    }
    for (int eidx = 0; eidx < am.e.length; eidx++) {
      pw.printf("<tr><td>(%d) %s</td>\n", eidx, escapeHtml(am.e[eidx]));
      for (int fidx = 0; fidx < am.f.length; fidx++) {
        if (am.fe[fidx][eidx]) {
          pw.printf("    <td bgcolor=\"black\">%d,%d</td>\n", fidx, eidx);
        } else {
          pw.println("  <td>&nbsp;</td>");
        }
      }
      pw.println("</tr>");
    }
    pw.println("</table>");
  }

  private static String escapeHtml(String str) {
    str = str.replaceAll("<", "&lt;");
    str = str.replaceAll(">", "&gt;");
    return str;
  }

  static void printConfusionMatrix(TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> m) {
    System.out.println("==================Confusion Matrix==================");
    System.out.print("->real");
    TreeSet<TrainingExamples.ReorderingTypes> firstKeySet = new TreeSet<TrainingExamples.ReorderingTypes>();
    firstKeySet.addAll(m.firstKeySet());
    TreeSet<TrainingExamples.ReorderingTypes> secondKeySet = new TreeSet<TrainingExamples.ReorderingTypes>();
    secondKeySet.addAll(m.secondKeySet());
    for (TrainingExamples.ReorderingTypes k : firstKeySet) {
      System.out.printf("\t"+k);
    }
    System.out.println();
    for (TrainingExamples.ReorderingTypes k2 : secondKeySet) {
      System.out.print(k2+"\t");
      for (TrainingExamples.ReorderingTypes k1 : firstKeySet) {
        System.out.print((int)m.getCount(k1,k2)+"\t");
      }
      System.out.println();
    }

    System.out.println("----------------------------------------------------");
    System.out.print("total\t");
    for (TrainingExamples.ReorderingTypes k1 : firstKeySet) {
      System.out.print((int)m.totalCount(k1)+"\t");
    }
    System.out.println();
    System.out.println("====================================================");
    System.out.println();
  }

  static void resultSummary(TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> confusionMatrix) {
    double totalNum = 0;
    double totalDenom = confusionMatrix.totalCount();
    for (TrainingExamples.ReorderingTypes k : confusionMatrix.firstKeySet()) {
      double num = confusionMatrix.getCount(k, k);
      totalNum += num;
    }
    System.out.printf("#total = %d |\tAcc:\t%f\n", (int)totalDenom, 100.0*totalNum/totalDenom);
  }
}