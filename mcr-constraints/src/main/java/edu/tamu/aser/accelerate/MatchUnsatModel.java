package edu.tamu.aser.accelerate;

import java.util.*;

public class MatchUnsatModel {
    // assert name - assert content 存储所有assert及其对编号，可能会比较多，后续可以优化
    private final HashMap<String, String> assertContentToNameMap = new HashMap<>();
    // assert content - assert name
    private final HashMap<String, String> assertNameToContentMap = new HashMap<>();
    // 全部的unsat-core
    private final TreeSet<HashSet<String>> unsatCoreSet = new TreeSet<>(new HashSetComparator());
    // 当前trace已命名的条件的名称集合
    private final HashSet<String> curAssertNameSet = new HashSet<>();
    // 当前trace的所有可能条件
    private final ArrayList<ArrayList<String>> curAllConditionsList = new ArrayList<>();
    // 计算了的smt求解个数
    public static long smtNum = 0;
    // 跳过了的smt求解个数
    public static long jumpNum = 0;
    private static MatchUnsatModel instance = null;
    // 统计下一个新assert的编号
    private long indexOfAssert = 0L;
    // 记录一次unsat-core匹配的开始时间
    private long stTime = 0;
    public long errorStTime = 0;
    // 当一次unsat-core的匹配时间大于等于standradTime时，停止匹配直接约束求解
    private long standradTime = 700;

    public static void main(String[] args) {
        TreeSet<HashSet<String>> treeSet = new TreeSet<>(new HashSetComparator());
        HashSet<String> hashSet = new HashSet<>();
        HashSet<String> hashSet2 = new HashSet<>();
        HashSet<String> hashSet3 = new HashSet<>();

        hashSet.add("A1");
        hashSet.add("A5");
        hashSet.add("A6");
        hashSet2.add("A6");
        hashSet2.add("A100");
        hashSet3.add("A6");
        hashSet3.add("A10");

        System.out.println(hashSet3);

    }

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

    /**
     * 添加unsat-core
     *
     * @param unsatCore z3求解后返回的unsat-core，例：A1 A3 A8
     */
    public void addUnsatCore(String unsatCore) {
        ArrayList<ArrayList<String>> unsatConditions = new ArrayList<>();

        for (String assertName : unsatCore.split(" ")) {
            String assertContent = assertNameToContentMap.get(assertName);
            unsatConditions.add(getAssertAllPossibleCondition(assertContent));
        }

        // 检查是否需要删除当前unsat-core或是否需要删除历史unsat-core
        for (HashSet<String> hashSet : getAssertsAllPossibleCondition(unsatConditions)) {
            boolean needAdd = true;

            for (HashSet<String> unsat : (TreeSet<HashSet<String>>) unsatCoreSet.clone()) {

                if (hashSet.size() >= unsat.size()) {
                    if (hashSet.containsAll(unsat)) {
                        needAdd = false;
                        break;
                    }

                } else {
                    if (unsat.containsAll(hashSet)) {
                        unsatCoreSet.remove(unsat);
                    }
                }
            }

            if (needAdd)
                unsatCoreSet.add(hashSet);
        }

    }

    /**
     * 检查当前trace的所有条件组合中是否包含已记录的unsat
     * 深度优先遍历所有条件，当算出一个完整条件后立即判段是否unsat-core
     *
     * @return true表示需要进行z3求解
     */
    public boolean checkTraceUnsat() {
        stTime = System.currentTimeMillis();
//        System.out.println(curAllConditionsList.size() + " * " + unsatCoreSet.size());

//        long numOfAllConditions = 1;
//        for (ArrayList<String> list : curAllConditionsList)
//            numOfAllConditions *= list.size();
//
//        System.out.println(numOfAllConditions);

        Boolean result = isUnsat(curAllConditionsList, unsatCoreSet, 0, "");


//        System.out.println(result + "\n");
        return result;
    }

    /**
     * @param conditions
     * @param unsatSet
     * @param indexOfI
     * @param cons
     * @return false表示结果已知，是unsat的；true表示结果未知，需要求解
     */
    private Boolean isUnsat(ArrayList<ArrayList<String>> conditions, TreeSet<HashSet<String>> unsatSet, int indexOfI, String cons) {
        Boolean result = false;

        if (indexOfI >= conditions.size()) {
            if (System.currentTimeMillis() - stTime >= standradTime)
                return true;

            HashSet<String> conss = new HashSet<>(Arrays.asList(cons.split(" ")));

            for (HashSet<String> unsat : unsatSet) {
                if (conss.containsAll(unsat))
                    return false;
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

    /**
     * 获得assert的命名
     */
    private String getAssertName(String assertContent) {
        if (!assertContentToNameMap.containsKey(assertContent)) {
            // 分别存储名称到内容和内容到名称的映射，方便读取
            assertContentToNameMap.put(assertContent, "A" + indexOfAssert);
            assertNameToContentMap.put("A" + indexOfAssert, assertContent);
            indexOfAssert++;
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

    /**
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

    /**
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
    private TreeSet<HashSet<String>> getAssertsAllPossibleCondition(ArrayList<ArrayList<String>> conditionsList) {
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
        }

        TreeSet<HashSet<String>> hashsetResult = new TreeSet<>(new HashSetComparator());
        for (String re : result) {
            hashsetResult.add(new HashSet<String>(Arrays.asList(re.split(" "))));
        }

        return hashsetResult;
    }

    public long getStandradTime() {
        return standradTime;
    }

    public void setStandradTime(long time) {
        standradTime = Math.max(standradTime, time);
    }
}
