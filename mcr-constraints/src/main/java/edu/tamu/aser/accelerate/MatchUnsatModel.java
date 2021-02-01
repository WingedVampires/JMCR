package edu.tamu.aser.accelerate;

import java.util.*;

public class MatchUnsatModel {
    private static MatchUnsatModel instance = null;
    // assert name - assert content 存储所有assert及其对编号，可能会比较多，后续可以优化
    private final HashMap<String, String> assertContentToNameMap = new HashMap<>();
    // assert content - assert name
    private final HashMap<String, String> assertNameToContentMap = new HashMap<>();
    // 全部的unsat-core
    private final HashSet<String> unsatCoreSet = new HashSet<>();
    // 当前trace已命名的条件的名称集合
    private final HashSet<String> curAssertNameSet = new HashSet<>();
    // 当前trace的所有可能条件
    private final ArrayList<ArrayList<String>> curAllConditionsList = new ArrayList<>();
    private long index = 0L;


    public static long smtNum = 0;
    public static long jumpNum = 0;

    public static MatchUnsatModel getInstance() {
        if (instance == null) {
            instance = new MatchUnsatModel();
        }

        return instance;
    }

    /**
     * 每此约束求解前初始化之前trace的所有记录的命名（z3语法要求）和所有条件的可能组合
     */
    public void init() {
        curAssertNameSet.clear();
        curAllConditionsList.clear();
    }

    public void printInfo() {
        System.out.println("所有可能条件：\n");
        for (String s : getAssertsAllPossibleCondition(curAllConditionsList))
            System.out.println(s);

        System.out.println("所有unsat-core：\n");

        for (String s : unsatCoreSet)
            System.out.println(s);

        System.out.println();
    }

    /**
     * 添加unsat-core
     *
     * @param unsatCore z3求解后返回的unsat-core，例：A1 A3 A8
     */
    public void addUnsatCore(String unsatCore) {
        //unsatSet.add(unsatCore.substring(1, unsatCore.length() - 1));
        ArrayList<ArrayList<String>> unsatConditions = new ArrayList<>();

        for (String assertName : unsatCore.split(" ")) {
            String assertContent = assertNameToContentMap.get(assertName);
            unsatConditions.add(getAssertAllPossibleCondition(assertContent));
        }

        unsatCoreSet.addAll(getAssertsAllPossibleCondition(unsatConditions));
    }

    /**
     * 检查当前trace的所有条件组合中是否包含已记录的unsat
     *
     * @return true表示需要进行z3求解
     */
    public boolean checkTraceUnsat() {
        int index = 0;
        // 计算当前trace的条件的所有组合
        ArrayList<String> traceAllPossibleCondition = getAssertsAllPossibleCondition(curAllConditionsList);

        for (String traceCondition : traceAllPossibleCondition) {
            // 一种组合的所有条件
            List<String> conditions = Arrays.asList(traceCondition.split(" "));

            for (String unsat : unsatCoreSet) {
                boolean isSat = false;

                // 判断是否有unsat-core
                for (String u : unsat.split(" ")) {
                    if (!conditions.contains(u)) {
                        isSat = true;
                        break;
                    }
                }

                //isSat值不变代表当前条件包含当前unsat-core的所有内容，即当前条件unsat，可以从trace的所有条件中删除当前条件
                if (!isSat) {
                    index++;//traceAllPossibleCondition.remove(traceCondition);
                    break;
                }

            }
        }

//        return !traceAllPossibleCondition.isEmpty();
        return index != traceAllPossibleCondition.size();
    }

    /**
     * 检查当前trace的所有条件组合中是否包含已记录的unsat
     * 深度优先遍历所有条件，当算出一个完整条件后立即判段是否unsat-core
     *
     * @return true表示需要进行z3求解
     */
    public boolean checkTraceUnsat_less_memory() {
        long numOfAllConditions = 1L;

        for (ArrayList<String> list : curAllConditionsList)
            numOfAllConditions *= list.size();

        if (numOfAllConditions / 1000000 > 0)
            return true;

        return isUnsat(curAllConditionsList, unsatCoreSet, 0, "");
    }

    /**
     * @param conditions
     * @param unsatSet
     * @param indexOfI
     * @param cons
     * @return false表示结果已知，是unsat的；true表示结果未知，需要求解
     */
    private Boolean isUnsat(ArrayList<ArrayList<String>> conditions, HashSet<String> unsatSet, int indexOfI, String cons) {
        Boolean result = false;
        int len = conditions.size();

        if (indexOfI >= len) {
            List<String> conss = Arrays.asList(cons.split(" "));

            for (String unsat : unsatSet) {
                boolean isSat = false;

                // 判断是否有unsat-core
                for (String u : unsat.split(" ")) {
                    if (!conss.contains(u)) {
                        isSat = true;
                        break;
                    }
                }

                //isSat值不变代表当前条件包含当前unsat-core的所有内容，即当前条件unsat，可以从trace的所有条件中删除当前条件
                if (!isSat) {
                    return false;
                }
            }

            return true;
        }

        for (int j = 0; j < conditions.get(indexOfI).size(); ++j) {
            result = result || isUnsat(conditions, unsatSet, indexOfI + 1, cons + " " + conditions.get(indexOfI).get(j));

            if (result) {
                break;
            }
        }

        return result;
    }

    // 获得在目标条件组合中某个unsat出现的个数
    private long getUnsatNum(ArrayList<ArrayList<String>> conditions, ArrayList<String> unsat, long mult, long elseMult) {
        long count = 0L;

        ArrayList<String> condition;
        ArrayList<ArrayList<String>> tmpCondition;
        ArrayList<String> tmpUnsat;

        if (unsat.size() == 0) // unsat都匹配上了
            return mult * elseMult;
        else if (conditions.size() > 0)
            condition = conditions.get(0);
        else
            return 0;

        int numOfCondition = condition.size();
        int index = 0;
        //剩余的条件们
        tmpCondition = (ArrayList<ArrayList<String>>) conditions.clone();
        tmpCondition.remove(condition);

        boolean haveUnsatElement = false;

        for (String con : condition) {
            haveUnsatElement = false;
            List<String> cons = Arrays.asList(con.split(" "));
            tmpUnsat = (ArrayList<String>) unsat.clone();

            for (String u : unsat) {
                if (cons.contains(u)) {
                    haveUnsatElement = true;
                    tmpUnsat.remove(u);
                }
            }

            if (haveUnsatElement)
                count += getUnsatNum(tmpCondition, tmpUnsat, mult, elseMult / numOfCondition);
            else
                index++;
        }

        //如果没有所需unsat-core内容
        if (index != 0)
            count += getUnsatNum(tmpCondition, unsat, mult * index, elseMult / numOfCondition);

        return count;
    }

    // 获得assert的命名
    private String getAssertName(String assertContent) {
        if (!assertContentToNameMap.containsKey(assertContent)) {
            // 分别存储名称到内容和内容到名称的映射，方便读取
            assertContentToNameMap.put(assertContent, "A" + index);
            assertNameToContentMap.put("A" + index, assertContent);
            index++;
        }

        return assertContentToNameMap.get(assertContent);
    }

    /**
     * 添加当前条件中已存在的命名
     * 如果已存在条件编号assertNum，则返回false；反之返回true
     *
     * @param assertNum
     * @return true表示该命名在当前trace中无记录
     */
    private boolean addCurAssertNames(String assertNum) {
        if (!curAssertNameSet.contains(assertNum)) {
            curAssertNameSet.add(assertNum);
            return true;
        }

        return false;
    }

    /***
     * 处理简单的原始或者单一的or的条件语句，即为条件命名，例：(< x1 x2 )
     * 同时记录条件的所有组合并且存储到{@link #curAllConditionsList}中
     *
     * @param simple
     * @return 修改后的assert语句
     */
    public String namedSimpleAssert(String simple) {
        String assertName = getAssertName(simple);

        curAllConditionsList.add(getAssertAllPossibleCondition(simple));

        if (addCurAssertNames(assertName))
            return "(assert (! " + simple + " :named " + assertName + " ) )\n";
        else
            return "(assert " + simple + " )\n";
    }

    /***
     * 将复杂的条件转换成简单的条件，即拆分and条件。同时分别对条件进行命名。or的条件整句命名，and的条件拆开后命名。
     * 记录条件的所有组合操作再{@link #namedSimpleAssert}中完成
     * @param complex
     * @return 修改后的assert语句
     */
    public StringBuilder namedComplexAssert(String complex) {
        StringBuilder result = new StringBuilder();

        AssertDetailInfo assertDetailInfo = AssertDetailInfo.getAssertDetailInfo(complex);

        if (assertDetailInfo == null) // 是一个简单条件
            result.append(namedSimpleAssert(complex));
        else {
            String left = assertDetailInfo.getLeft();
            String right = assertDetailInfo.getRight();
            String relation = assertDetailInfo.getRelation();
            AssertDetailInfo leftAssertDetailInfo = AssertDetailInfo.getAssertDetailInfo(left);
            AssertDetailInfo rightAssertDetailInfo = AssertDetailInfo.getAssertDetailInfo(right);

            if (relation.equals("AND")) {
                // and条件拆开后命名
                if (leftAssertDetailInfo == null)
//                result.append("(assert " + namedSimpleAssert(left) + " )\n");
                    result.append(namedSimpleAssert(left));
                else
                    result.append(namedComplexAssert(left));

                if (rightAssertDetailInfo == null)
//                result.append("(assert " + namedSimpleAssert(right) + " )\n");
                    result.append(namedSimpleAssert(right));
                else
                    result.append(namedComplexAssert(right));
            } else if (relation.equals("OR")) {
                // or条件整句命名
//            result.append("(assert " + namedSimpleAssert(complex) + " )\n");
                result.append(namedSimpleAssert(complex));
            }

        }

        return result;
    }

    // 获得一个条件语句的所有可能组合
    private ArrayList<String> getAssertAllPossibleCondition(String ass) {
        ArrayList<String> result = new ArrayList<>();

        AssertDetailInfo assertDetailInfo = AssertDetailInfo.getAssertDetailInfo(ass);

        if (assertDetailInfo == null) { // ass是一个简单条件
            result.add(getAssertName(ass)); // 获取名称并返回
        } else { //ass是一个复杂条件
            String left = assertDetailInfo.getLeft();
            String right = assertDetailInfo.getRight();
            String relation = assertDetailInfo.getRelation();

            if (relation.equals("OR")) { // or的组合
                result.addAll(getAssertAllPossibleCondition(left));
                result.addAll(getAssertAllPossibleCondition(right));
            } else if (relation.equals("AND")) { // and的组合
                for (String l : getAssertAllPossibleCondition(left)) {
                    for (String r : getAssertAllPossibleCondition(right)) {
                        result.add(l + " " + r);
                    }
                }
            }
        }

        return result;
    }

    /**
     * @param conditionsList 需要计算的条件的组合的list
     * @return 所有可能组合
     */
    private ArrayList<String> getAssertsAllPossibleCondition(ArrayList<ArrayList<String>> conditionsList) {
        ArrayList<String> tem;
        ArrayList<String> list;
        String s;
        ArrayList<String> result = new ArrayList<>();

        ArrayList<String> tmp = new ArrayList<>();
        if (conditionsList.size() > 0) result.addAll(conditionsList.get(0));

        for (int i = 1; i < conditionsList.size(); ++i) {
            list = conditionsList.get(i);

            for (String r : result)
                for (String l : list) {
                    s = r + " " + l;
                    tmp.add(s);

                }


            tem = result;
            result = tmp;
            tmp = tem;
            tmp.clear();
//            result.clear();
//            result.addAll(tmp);
//            tmp.clear();
        }

        return result;
    }


}
