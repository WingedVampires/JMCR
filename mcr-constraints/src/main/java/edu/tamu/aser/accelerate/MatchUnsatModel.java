package edu.tamu.aser.accelerate;

import java.util.*;

public class MatchUnsatModel {
    private static MatchUnsatModel instance = null;
    // assert name - assert content 存储所有assert及其对编号，可能会比较多，后续可以优化
    private final HashMap<String, String> assertContentToNameMap = new HashMap<>();
    // assert content - assert name
    private final HashMap<String, String> assertNameToContentMap = new HashMap<>();
    // 全部的unsat-core
    private final HashSet<String> unsatSet = new HashSet<>();
    // 当前trace已命名的条件的名称集合
    private final HashSet<String> curAssertNameSet = new HashSet<>();
    // 当前trace的所有可能条件
    private final ArrayList<ArrayList<String>> curAllConditionsList = new ArrayList<>();
    private long index = 0L;
    private int time = 1;

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

        for (String s : unsatSet)
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

        unsatSet.addAll(getAssertsAllPossibleCondition(unsatConditions));
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

            for (String unsat : unsatSet) {
                boolean isSat = false;

                // 判断是否有unsat-core
                for (String u : unsat.split(" ")) {
                    if (!conditions.contains(u)) {
                        isSat = true;
                        break;
                    }
                }

                //isSat值不变代表当前条件包含当前unsat-core的所有内容，即当前条件unsat，可以从trace的所有条件中删除当前条件
                if (!isSat)
                    index++;//traceAllPossibleCondition.remove(traceCondition);
            }
        }

//        return !traceAllPossibleCondition.isEmpty();
        return index != traceAllPossibleCondition.size();
    }

    // 获得assert的命名
    public String getAssertName(String assertContent) {
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
    public boolean addCurAssertNames(String assertNum) {
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

//        if (addCurAssertNames(assertName))
//            return "(! " + simple + " :named " + assertName + " )";
//        else
//            return simple;

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

            if (relation == "AND") {
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
            } else if (relation == "OR") {
                // or条件整句命名
//            result.append("(assert " + namedSimpleAssert(complex) + " )\n");
                result.append(namedSimpleAssert(complex));
            }

        }

        return result;
    }

    // 获得一个条件语句的所有可能组合
    public ArrayList<String> getAssertAllPossibleCondition(String ass) {
        ArrayList<String> result = new ArrayList<>();

        AssertDetailInfo assertDetailInfo = AssertDetailInfo.getAssertDetailInfo(ass);

        if (assertDetailInfo == null) { // ass是一个简单条件
            result.add(getAssertName(ass)); // 获取名称并返回
        } else { //ass是一个复杂条件
            String left = assertDetailInfo.getLeft();
            String right = assertDetailInfo.getRight();
            String relation = assertDetailInfo.getRelation();

            if (relation == "OR") { // or的组合
                result.addAll(getAssertAllPossibleCondition(left));
                result.addAll(getAssertAllPossibleCondition(right));
            } else if (relation == "AND") { // and的组合
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
    public ArrayList<String> getAssertsAllPossibleCondition(ArrayList<ArrayList<String>> conditionsList) {
        ArrayList<String> result = new ArrayList<>();

        ArrayList<String> tmp = new ArrayList<>();
        if (conditionsList.size() > 0) result.addAll(conditionsList.get(0));

        for (int i = 1; i < conditionsList.size(); ++i) {
            ArrayList<String> list = conditionsList.get(i);

            for (String r : result)
                for (String l : list)
                    tmp.add(r + " " + l);

            result = tmp;
            tmp = new ArrayList<>();
        }

        return result;
    }


}
