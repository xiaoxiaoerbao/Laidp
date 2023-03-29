package laidp;

import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class Solution {

    /**
     * 需要性能优化
     * @param srcGenotype the first dim is haplotype, the second dim is SNP position
     * @param queryGenotype queryGenotype, 0 means reference allele, 1 means alternative allele
     * @param switchCostScore switchCostScore
     * @return mini cost score matrix, the first dim is haplotype, the second dim is SNP position
     */
    public static double[][] getMiniCostScore(int[][] srcGenotype, int[] queryGenotype,
                                              double switchCostScore){
        //        double switchCostScore= 1.5;
//        int[][] srcGenotype = {{0,1,0,1,0,1,0,0,0,0,1,1},
//                            {0,0,0,1,0,1,1,0,0,0,1,1},
//                            {0,0,1,0,1,0,0,0,1,0,1,1},
//                            {0,0,0,0,1,0,1,0,1,1,1,1},
//                            {1,1,0,0,0,0,1,1,1,1,0,0},
//                            {1,0,0,1,0,0,1,1,1,1,0,0}};
//        int[] queryGenotype =       {1,1,0,0,0,1,0,0,1,1,1,1};

//        long start = System.nanoTime();
        int rowNum = srcGenotype.length;
        int colNum = srcGenotype[0].length;

        // distance
        double[][] distance = new double[rowNum][colNum];
        for (int i = 0; i < rowNum; i++) {
            for (int j = 0; j < colNum; j++) {
                distance[i][j]=Math.abs(srcGenotype[i][j]- queryGenotype[j]);
            }
        }

        // initialize mini cost score
        double[][] miniCost = new double[rowNum][colNum];
        for (int i = 0; i < miniCost.length; i++) {
            miniCost[i][0] = distance[i][0];
        }


        // i is SNP position
        // j is haplotype index of source population
        // miniCost
        for (int i = 1; i < colNum; i++) {

            // j-1 SNP位置，单倍型路径发生switch对应的最小Cost
            double miniCostSwitch=Double.MAX_VALUE;
            for (int j = 0; j < distance.length; j++) {
                miniCostSwitch = Math.min(miniCost[j][i - 1], miniCostSwitch);
            }

//            for (int j = 0; j < rowNum; j++) {
//                // 最小cost路径对应当前haplotype
//                if (miniCost[j][i-1] < miniCostSwitch+switchCostScore){
//                    miniCost[j][i] = miniCost[j][i-1] + distance[j][i];
//                }else {
//                    // 最小cost路径对应转换单倍型
//                    miniCost[j][i] = miniCostSwitch+switchCostScore+distance[j][i];
//                }
//            }

            // Calculate miniCost matrix for current SNP position
            for (int j = 0; j < rowNum; j++) {
                miniCost[j][i] = miniCostSwitch + switchCostScore;
                if (miniCost[j][i - 1] < miniCostSwitch + switchCostScore) {
                    miniCost[j][i] = miniCost[j][i - 1];
                }
                miniCost[j][i] += distance[j][i];
            }
        }
//        System.out.println("calculate mini cost matrix take "+Benchmark.getTimeSpanSeconds(start)+ " seconds");
        return miniCost;
    }

    /**
     * 需要性能优化
     * @param srcGenotype the first dim is haplotype, the second dim is SNP position
     * @param queryGenotype queryGenotype, 0 means reference allele, 1 means alternative allele
     * @param switchCostScore switchCostScore
     * @param srcIndiList srcIndiList, order must same as srcGenotype
     * @param taxaSourceMap taxaSourceMap
     * @return candidate solution, it consists of multiple solutions with the same mini cost score
     * dim1 is mini cost score index, dim2 is solution
     * Solution consists of multiple groups, every three numbers as a group, representing a tract
     * the first number is source population index, equal WindowSource.Source.index()
     * the second and third number is start(inclusive) position and end(inclusive) position
     */
    public static IntList[] getCandidateSolution(int[][] srcGenotype, int[] queryGenotype, double switchCostScore,
                                                 List<String> srcIndiList, Map<String, Source> taxaSourceMap) {

        double[][] miniCost = Solution.getMiniCostScore(srcGenotype, queryGenotype, switchCostScore);
        int haplotypeLen = miniCost[0].length;

        // Find the index of the minimum cost score
        double miniCostScore = Double.MAX_VALUE;
        IntList miniCostScoreIndexList = new IntArrayList();
        for (int i = 0; i < miniCost.length; i++) {
            if (miniCost[i][haplotypeLen-1] < miniCostScore) {
                miniCostScore = miniCost[i][haplotypeLen-1];
                miniCostScoreIndexList.clear();
                miniCostScoreIndexList.add(i);
            } else if (miniCost[i][haplotypeLen-1] == miniCostScore) {
                miniCostScoreIndexList.add(i);
            }
        }

        // Initialize the candidate solutions
        IntList[] solutions = new IntList[miniCostScoreIndexList.size()];
        for (int i = 0; i < solutions.length; i++) {
            solutions[i] = new IntArrayList();
            Source source = taxaSourceMap.get(srcIndiList.get(miniCostScoreIndexList.getInt(i)));
            solutions[i].add(Source.valueOf(source.name()).getFeature());
            solutions[i].add(haplotypeLen-1);
            solutions[i].add(haplotypeLen-1);
        }

        // Compute the candidate solutions
        for (int i = 0; i < solutions.length; i++) {
            IntSet currentIndexSet = new IntOpenHashSet();
            currentIndexSet.add(miniCostScoreIndexList.getInt(i));
            int currentSolutionElementIndex = 0;

            for (int j = haplotypeLen - 1; j > 0; j--) {
                IntSet nextIndexSet = new IntOpenHashSet();

                for (IntIterator it = currentIndexSet.iterator(); it.hasNext(); ) {
                    int index = it.nextInt();

                    // Add the current index to the next index set if the cost of staying at the current index is lower
                    if (miniCost[index][j-1] <= miniCost[index][j]) {
                        nextIndexSet.add(index);
                    }

                    // Add the indices of other individuals to the next index set if the cost of switching to them is lower
                    for (int k = 0; k < miniCost.length; k++) {
                        if (k != index && (miniCost[k][j-1] + switchCostScore) <= miniCost[index][j]) {
                            nextIndexSet.add(k);
                        }
                    }
                }

                int currentSourceFeature = Solution.getSourceFutureFrom(currentIndexSet, srcIndiList, taxaSourceMap);
                int nextSourceFeature = Solution.getSourceFutureFrom(nextIndexSet, srcIndiList, taxaSourceMap);
                if (currentSourceFeature == nextSourceFeature) {
                    solutions[i].set(currentSolutionElementIndex * 3 + 2, j-1);
                } else {
                    solutions[i].add(nextSourceFeature);
                    solutions[i].add(j-1);
                    solutions[i].add(j-1);
                    currentSolutionElementIndex++;
                }

                currentIndexSet = nextIndexSet;
            }
        }

        return solutions;
    }

    private static int getSourceFutureFrom(IntSet sourceIndexSet, List<String> srcIndiList,
                                           Map<String, Source> taxaSourceMap){
        EnumSet<Source> sourceEnumSet = EnumSet.noneOf(Source.class);
        for (int index : sourceIndexSet){
            sourceEnumSet.add(taxaSourceMap.get(srcIndiList.get(index)));
        }

        return Source.getSourceFeature(sourceEnumSet);
    }

    public static int[] getSolution(BitSet[] srcGenotypeFragment,
                                    BitSet queryGenotypeFragment,
                                    int fragmentLength,
                                    double switchCostScore,
                                    List<String> srcIndiList,
                                    Map<String, Source> taxaSourceMap,
                                    int maxSolutionCount){
        int[][] srcGenotype = new int[srcGenotypeFragment.length][fragmentLength];
        int[] queryGenotype = new int[fragmentLength];
        for (int i = 0; i < srcGenotype.length; i++) {
            for (int j = srcGenotypeFragment[i].nextSetBit(0); j >= 0; j=srcGenotypeFragment[i].nextSetBit(j+1)) {
                srcGenotype[i][j] = 1;
            }
        }
        for (int i = queryGenotypeFragment.nextSetBit(0); i >= 0; i = queryGenotypeFragment.nextSetBit(i+1)) {
            queryGenotype[i] = 1;
        }

        IntList[] forwardCandidateSolutionCurrent = Solution.getCandidateSolution(srcGenotype,
                queryGenotype, switchCostScore, srcIndiList, taxaSourceMap);
        IntList[] forwardCandidateSolutionNext = Solution.getCandidateSolution(srcGenotype,
                queryGenotype, switchCostScore+1, srcIndiList, taxaSourceMap);

        int totalSolutionSizeCurrent = Solution.getMiniOptimalSolutionSize(forwardCandidateSolutionCurrent);
        int totalSolutionSizeNext = Solution.getMiniOptimalSolutionSize(forwardCandidateSolutionNext);

        //  totalSolutionSizeCurrent < 0 是因为 两个Int相乘的结果大于Int max
        if ((totalSolutionSizeCurrent > maxSolutionCount && totalSolutionSizeNext <= totalSolutionSizeCurrent/2) || totalSolutionSizeCurrent <= 0){
            return Solution.getSolution(srcGenotypeFragment, queryGenotypeFragment,
                    fragmentLength, switchCostScore+1, srcIndiList, taxaSourceMap, maxSolutionCount);
        }

        int[] forwardSolution = Solution.coalescentForward(forwardCandidateSolutionCurrent);
        if (forwardSolution.length==0) return forwardSolution;
        int seqLen = forwardSolution.length;
        IntList[] reverseCandidateSolution;
        int[] reverseSolution;
        IntList singleSourceFeatureList = Source.getSingleSourceFeatureList();
        singleSourceFeatureList.rem(1);
        if (singleSourceFeatureList.contains(forwardSolution[seqLen-1])){
            reverseCandidateSolution = Solution.getCandidateSolution(Solution.reverseSrcGenotype(srcGenotype),
                    Solution.reverseGenotype(queryGenotype),switchCostScore, srcIndiList, taxaSourceMap);
            reverseSolution = Solution.coalescentReverse(reverseCandidateSolution);
            for (int i = seqLen - 1; i > -1; i--) {
                if (reverseSolution[i]==1){
                    forwardSolution[i] = 1;
                }else {
                    break;
                }
            }
        }
        return forwardSolution;
    }

    /**
     * loter-like or major vote
     */
    public static int[] getLoterSolution(BitSet[] srcGenotypeFragment,
                                         BitSet queryGenotypeFragment,
                                         int fragmentLength,
                                         double switchCostScore,
                                         List<String> srcIndiList,
                                         Map<String, Source> taxaSourceMap,
                                         int maxSolutionCount){
        int[][] srcGenotype = new int[srcGenotypeFragment.length][fragmentLength];
        int[] queryGenotype = new int[fragmentLength];
        for (int i = 0; i < srcGenotype.length; i++) {
            for (int j = srcGenotypeFragment[i].nextSetBit(0); j >= 0; j=srcGenotypeFragment[i].nextSetBit(j+1)) {
                srcGenotype[i][j] = 1;
            }
        }
        for (int i = queryGenotypeFragment.nextSetBit(0); i >= 0; i = queryGenotypeFragment.nextSetBit(i+1)) {
            queryGenotype[i] = 1;
        }


        double[] switchCostScores = {1.5};
        IntList[] candidateSolutions;
        int[] solution;
        List<int[]> solutions = new ArrayList<>();
        int sourceFeature, start, end;
        for (int i = 0; i < switchCostScores.length; i++) {
            candidateSolutions = Solution.getCandidateSolution(srcGenotype, queryGenotype,
                    switchCostScores[i], srcIndiList, taxaSourceMap);
            for (int j = 0; j < candidateSolutions.length; j++) {
                solution = new int[fragmentLength];
                for (int k = 0; k < candidateSolutions[j].size(); k=k+3) {
                    sourceFeature = candidateSolutions[j].get(k);
                    start = candidateSolutions[j].get(k+2); // inclusive
                    end = candidateSolutions[j].get(k+1); // inclusice
                    Arrays.fill(solution, start, end+1, sourceFeature);
                }
                solutions.add(solution);
            }
        }
        int solutionCount = solutions.size();
        int[] finalSolution = new int[fragmentLength];
        Int2IntMap countMap;
        for (int posIndex = 0; posIndex < fragmentLength; posIndex++) {
            int maxCount = -1;
            int mode = -1;
            countMap = new Int2IntArrayMap();
            for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
                sourceFeature = solutions.get(solutionIndex)[posIndex];
                int feature;
                for (int i = 1; i <= sourceFeature; i<<=1) {
                    feature = sourceFeature & i;
                    if (feature == 0) continue;
                    int count = countMap.getOrDefault(feature, 0) + 1;
                    countMap.put(feature, count);
                    if (count > maxCount){
                        mode = feature;
                        maxCount = count;
                    }
                }
            }
            finalSolution[posIndex] = mode;
        }

        return finalSolution;
    }

    /**
     * loter-like or major vote
     */
    public static int[] majorVote(List<IntList> candidateSolutions,
                                         int fragmentLength){

        int[] solution;
        List<int[]> solutions = new ArrayList<>();
        int sourceFeature, start, end;
        for (int j = 0; j < candidateSolutions.size(); j++) {
            solution = new int[fragmentLength];
            for (int k = 0; k < candidateSolutions.get(j).size(); k=k+3) {
                sourceFeature = candidateSolutions.get(j).get(k);
                start = candidateSolutions.get(j).get(k+1); // inclusive
                end = candidateSolutions.get(j).get(k+2); // inclusice
                Arrays.fill(solution, start, end+1, sourceFeature);
            }
            solutions.add(solution);
        }
        int solutionCount = solutions.size();
        int[] finalSolution = new int[fragmentLength];
        Int2IntMap countMap;
        for (int posIndex = 0; posIndex < fragmentLength; posIndex++) {
            int maxCount = -1;
            int mode = -1;
            countMap = new Int2IntArrayMap();
            for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
                sourceFeature = solutions.get(solutionIndex)[posIndex];
                int feature;
                for (int i = 1; i <= sourceFeature; i<<=1) {
                    feature = sourceFeature & i;
                    if (feature == 0) continue;
                    int count = countMap.getOrDefault(feature, 0) + 1;
                    countMap.put(feature, count);
                    if (count > maxCount){
                        mode = feature;
                        maxCount = count;
                    }
                }
            }
            finalSolution[posIndex] = mode;
        }

        return finalSolution;
    }

    public static int getMiniOptimalSolutionSize(IntList[] solutions){
        int[] size = Solution.getOptimalSolutionsSize(solutions);
        int mini = Integer.MAX_VALUE;
        for (int j : size) {
            mini = Math.min(j, mini);
        }
        return mini;
    }

    public static int[] getOptimalSolutionsSize(IntList[] solutions){
        int[] sizes = new int[solutions.length];
        Arrays.fill(sizes, -1);
        int cumSize=1;
        EnumSet<Source> sources;
        for (int i = 0; i < solutions.length; i++) {
            for (int j = solutions[i].size()-1; j > 0; j=j-3) {
                sources = Source.getSourcesFrom(solutions[i].getInt(j-2));
                if (sources.size()==1) continue;
                cumSize*=sources.size();
            }
            sizes[i] = cumSize;
            cumSize = 1;
        }
        return sizes;
    }

    public static int[][] reverseSrcGenotype(int[][] srcGenotype){
        int[][] reverseGenotype = new int[srcGenotype.length][];
        for (int i = 0; i < reverseGenotype.length; i++) {
            reverseGenotype[i] = new int[srcGenotype[0].length];
            Arrays.fill(reverseGenotype[i], -1);
        }

        for (int i = 0; i < srcGenotype.length; i++) {
            for (int j = 0; j < srcGenotype[i].length; j++) {
                reverseGenotype[i][srcGenotype[i].length-1-j]=srcGenotype[i][j];
            }
        }
        return reverseGenotype;
    }

    /**
     *
     * @param genotype genotype
     * @return 反向序列
     */
    public static int[] reverseGenotype(int[] genotype){
        int[] reverseGenotype = new int[genotype.length];
        Arrays.fill(reverseGenotype, -1);
        for (int i = 0; i < genotype.length; i++) {
            reverseGenotype[genotype.length-1-i]=genotype[i];
        }
        return reverseGenotype;
    }

    public static int getMiniSolutionEleCount(IntList[] solutions){
        int miniSolutionEleCount = Integer.MAX_VALUE;
        for (IntList solution : solutions) {
            miniSolutionEleCount = Math.min(solution.size(), miniSolutionEleCount);

        }
        return miniSolutionEleCount;
    }

    public static int[] coalescentForward(IntList[] solutions){
        int[] miniSolutionSizeArray = Solution.getOptimalSolutionsSize(solutions);
        int miniSolutionSize = Solution.getMiniOptimalSolutionSize(solutions);
        int miniSolutionEleCount = Solution.getMiniSolutionEleCount(solutions);
        int solutionEleCount;
        Set<IntList> solutionSet = new HashSet<>();
        for (int i = 0; i < solutions.length; i++) {
            if (miniSolutionSizeArray[i]!=miniSolutionSize) continue;
            if (solutions[i].size()!=miniSolutionEleCount) continue;
            solutionEleCount =  solutions[i].size();
            // filter Source is NATIVE
            if (solutionEleCount==3 && (solutions[i].getInt(0)==Source.NATIVE.getFeature())) continue;
            solutionSet.add(solutions[i]);
        }
        List<IntList> solutionList = new ArrayList<>(solutionSet);
        if (solutionList.size()==0) return new int[0];
        int[] targetSourceCumLen = Solution.getTargetSourceCumLen(solutionList);
        int maxTargetSourceCumLen = Integer.MIN_VALUE;
        for (int j : targetSourceCumLen) {
            maxTargetSourceCumLen = Math.max(j, maxTargetSourceCumLen);
        }
        List<IntList> solutionList2 = new ArrayList<>();
        for (int i = 0; i < targetSourceCumLen.length; i++) {
            if (targetSourceCumLen[i] == maxTargetSourceCumLen){
                solutionList2.add(solutionList.get(i));
            }
        }

        int solutionCount = solutionList2.size();
        List<IntList> forwardSolutions = new ArrayList<>();
        IntList maxTargetSourceCumLenSolution, solutionRes;
        int fragmentLen = solutionList2.get(0).getInt(1)+1;
        IntList introgressedFeatureList = Source.getSingleSourceFeatureList();
        introgressedFeatureList.rem(1);
        for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
            maxTargetSourceCumLenSolution = solutionList2.get(solutionIndex);
            solutionRes = new IntArrayList();
            int sourceFeature, start, end;
            for (int i = maxTargetSourceCumLenSolution.size()-1; i > 0; i=i-3) {
                sourceFeature = maxTargetSourceCumLenSolution.getInt(i-2);
                start = maxTargetSourceCumLenSolution.getInt(i);
                end = maxTargetSourceCumLenSolution.getInt(i-1);
//                if (introgressedFeatureList.contains(sourceFeature) && (end-start+1) < fragmentLen/20){
//                    sourceFeature = 1; // 片段过短, 可能是ILS, 因此设为native ancestry
//                }
                solutionRes.add(sourceFeature);
                solutionRes.add(start);
                solutionRes.add(end);
            }
            forwardSolutions.add(solutionRes);
        }

        return majorVote(forwardSolutions, fragmentLen);
    }

    public static int[] coalescentReverse(IntList[] solutions){
        int[] miniSolutionSizeArray = Solution.getOptimalSolutionsSize(solutions);
        int miniSolutionSize = Solution.getMiniOptimalSolutionSize(solutions);
        int miniSolutionEleCount = Solution.getMiniSolutionEleCount(solutions);
        int solutionEleCount;
        Set<IntList> solutionSet = new HashSet<>();
        for (int i = 0; i < solutions.length; i++) {
            if (miniSolutionSizeArray[i]!=miniSolutionSize) continue;
            if (solutions[i].size()!=miniSolutionEleCount) continue;
            solutionEleCount =  solutions[i].size();
            // filter Source is NATIVE
            if (solutionEleCount==3 && (solutions[i].getInt(0)==Source.NATIVE.getFeature())) continue;
            solutionSet.add(solutions[i]);
        }
        List<IntList> solutionList = new ArrayList<>(solutionSet);
        if (solutionList.size()==0) return new int[0];
        int[] targetSourceCumLen = Solution.getTargetSourceCumLen(solutionList);
        int maxTargetSourceCumLen = Integer.MIN_VALUE;
        for (int j : targetSourceCumLen) {
            maxTargetSourceCumLen = Math.max(j, maxTargetSourceCumLen);
        }

        List<IntList> solutionList2 = new ArrayList<>();
        for (int i = 0; i < targetSourceCumLen.length; i++) {
            if (targetSourceCumLen[i] == maxTargetSourceCumLen){
                solutionList2.add(solutionList.get(i));
            }
        }
        int solutionCount = solutionList2.size();
        List<IntList> reverseSolutions = new ArrayList<>();
        IntList solutionRes;
        IntList maxTargetSourceCumLenSolution;
        int seqLen = solutionList2.get(0).getInt(1) + 1;
        IntList introgressedFeatureList = Source.getSingleSourceFeatureList();
        introgressedFeatureList.rem(1);
        for (int solutionIndex = 0; solutionIndex < solutionCount; solutionIndex++) {
            solutionRes = new IntArrayList();
            maxTargetSourceCumLenSolution = solutionList2.get(solutionIndex);
            int sourceFeature, start, end;
            for (int i = 0; i < maxTargetSourceCumLenSolution.size(); i=i+3) {
                sourceFeature = maxTargetSourceCumLenSolution.getInt(i);
                start = seqLen-1-maxTargetSourceCumLenSolution.getInt(i+1);
                end = seqLen-1-maxTargetSourceCumLenSolution.getInt(i+2);
//                if (introgressedFeatureList.contains(sourceFeature) && (end-start+1) < seqLen/20){
//                    sourceFeature = 1; // 片段过短, 可能是ILS, 因此设为native ancestry
//                }
                solutionRes.add(sourceFeature);
                solutionRes.add(start);
                solutionRes.add(end);
            }
            reverseSolutions.add(solutionRes);
        }

        return majorVote(reverseSolutions, seqLen);
    }

    public static int[] getTargetSourceCumLen(List<IntList> solutions){
        int[] cumLen = new int[solutions.size()];
        IntList singleSourceFeatureList = Source.getSingleSourceFeatureList();
        for (int i = 0; i < solutions.size(); i++) {
            for (int j = 0; j < solutions.get(i).size(); j=j+3) {
                if (singleSourceFeatureList.contains(solutions.get(i).getInt(j))){
                    cumLen[i]+=(solutions.get(i).getInt(j+1)) - (solutions.get(i).getInt(j+2));
                }
            }
        }
        return cumLen;
    }
}
