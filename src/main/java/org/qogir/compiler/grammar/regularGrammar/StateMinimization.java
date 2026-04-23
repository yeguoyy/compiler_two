package org.qogir.compiler.grammar.regularGrammar;


import org.qogir.compiler.FA.State;
import org.qogir.compiler.util.graph.LabelEdge;
import org.qogir.compiler.util.graph.LabeledDirectedGraph;

import java.util.*;

public class StateMinimization {

    /**
     * Distinguish non-equivalent states in the given DFA.
     *
     * @param dfa the original dfa.
     * @return distinguished equivalent state groups
     */
    public HashMap<Integer, HashMap<Integer, State>> distinguishEquivalentState(RDFA dfa) {
        //Add your implementation

        HashMap<Integer,HashMap<Integer, State>> groupSet = new HashMap<>(); // group set
        return groupSet;
    }

    public RDFA minimize(RDFA dfa) {
//      Map<HashMap<Integer, State>, State> dfaStateMap = new HashMap<>();// DFA状态映射表 旧状态集合 -> 新状态
        List<Set<State>> stateSetList = new ArrayList<>();//用于分组
        LabeledDirectedGraph<State> originalGraph = dfa.getTransitTable();
        ArrayDeque<String> stepQueue = new ArrayDeque<>();

        //initialize
        for (int i = 0; i <= 2; i++){
            Set<State> group = new HashSet<>();
            for(State s: originalGraph.vertexSet()){
                if(s.getType() == i){
                    group.add(s);
                }
            }
            if (!group.isEmpty()) stateSetList.add(group);
        }
        recordDistinguishSteps(stepQueue, stateSetList, "initial split");
        Set<Character> alphabet = getAlphabet(originalGraph);

        //分离集合
        boolean isChanged = true;
        while (isChanged) {
            isChanged = false;
            for (char c : alphabet) {
                List<Set<State>> newStateSetList = new ArrayList<>();
                for(Set<State> currentGroup: stateSetList){
                    //如果集合中只有一个元素，无法再分组
                    if(currentGroup.size() <= 1){
                        newStateSetList.add(currentGroup);
                        continue;
                    }

                    //获取状态集合的“行为签名”，用于存储和划分不同组的状态
                    Map<Integer, Set<State>> splitBuckets = new HashMap<>();
                    for (State s : currentGroup) {
                        State nextState = getSuccessor(originalGraph, s, c);
                        int targetGroupIndex = -1; // -1 表示死路（没有跳转）
                        if (nextState != null) {
                            // 如果不是空的，找到这个状态的状态集合编号
                            targetGroupIndex = stateSetList.indexOf(findGroup(stateSetList, nextState));
                        }
                        // 添加状态 s 到对应的组 （循环后会把这个集合全部s都添加进去）
                        splitBuckets.computeIfAbsent(targetGroupIndex, k -> new HashSet<>()).add(s);
                    }

                    // 如果拆分出的桶多于 1 个，说明 currentGroup 需要拆分
                    if (splitBuckets.size() > 1) {
                        isChanged = true;
                        // 将拆分后的每个桶加入新的划分列表
                        newStateSetList.addAll(splitBuckets.values());
                    } else {
                        // 不需要拆分，保持原样
                        newStateSetList.add(currentGroup);
                    }
                }
                //更新
                stateSetList = newStateSetList;
                recordDistinguishSteps(stepQueue, stateSetList, "end of_"+ c);
            }
        }
        showDistinguishSteps(stepQueue,stateSetList);

        //处理边
        RDFA miniDFA = new RDFA();
        State.STATE_ID = 0;
        Map<Set<State>, State> groupToNewState = new HashMap<>();//旧状态集合 -> 新状态
        //创建新状态
        for(Set<State> group: stateSetList){
            State newState = new State();

            int newType = 0; // 默认为 0
            // 1. 先检查有没有最高优先级的（终态）
            boolean hasFinal = group.stream().anyMatch(s -> s.getType() == 2);
            if (hasFinal) {
                newType = 2;
            }
            // 2. 如果没有终态，再检查有没有次级优先级的（Type 1）
            else {
                boolean hasType1 = group.stream().anyMatch(s -> s.getType() == 1);
                if (hasType1) {
                    newType = 1;
                }
            }
            newState.setType(newType);
            miniDFA.getTransitTable().addVertex(newState);
            HashMap<Integer, State> groupMap = new HashMap<>();
            for(State s : group) groupMap.put(s.getId(), s);
            miniDFA.setStateMappingBetweenDFAAndNFA(newState, groupMap);

            groupToNewState.put(group, newState);
        }

        // 2. 确定起始状态
        State originalStart = dfa.getStartState();
        for (Set<State> group : stateSetList) {
            if (group.contains(originalStart)) {
                miniDFA.setStartState(groupToNewState.get(group));
                break;
            }
        }

        // 3. 添加边
        for(Set<State> group : stateSetList){
            State currentNewState = groupToNewState.get(group);//新状态
            State representative = group.iterator().next();//状态集合的代表（状态集合的第一个元素）
            for (char c : alphabet) {
                State nextOriginal = getSuccessor(originalGraph, representative, c);//下一个状态（原始）
                if (nextOriginal != null) {
                    Set<State> targetGroup = findGroup(stateSetList, nextOriginal);
                    if (targetGroup != null) {
                        State targetNewState = groupToNewState.get(targetGroup);
                        miniDFA.getTransitTable().addEdge(currentNewState, targetNewState, c);
                    }
                }
            }
        }
        return miniDFA;
    }

    private Set<Character> getAlphabet(LabeledDirectedGraph<State> graph) {
        //获取NFA的输入符号集
        Set<Character> alphabet = new HashSet<>();
        for (LabelEdge edge : graph.edgeSet()) {
            Character label = edge.getLabel();
            // 排除 ε，只保留实际字符
            if (label != null && !label.equals('ε')) {
                alphabet.add(label);
            }
        }
        return alphabet;
    }

    // 获取状态 s 在输入 c 下的后继状态
    private State getSuccessor(LabeledDirectedGraph<State> graph, State s, char c) {
        for(LabelEdge le : graph.edgeSet()){
            if(le.getSource().equals(s) && le.getLabel().equals(c)){
                State target = (State) le.getTarget();// 获取新状态
                return target;
            }
            else continue;
        }
        return null;
    }

    // 在划分列表中找到包含状态 s 的那个组
    private Set<State> findGroup(List<Set<State>> partitions, State s) {
        for (Set<State> group : partitions) {
            if (group.contains(s)) {
                return group;
            }
        }
        return null;
    }
    /**
     * 记录区分步骤
     *
     * @param stepQueue   用于存储步骤的队列
     * @param stateSetList 当前的等价类分组列表 (List<Set<State>>)
     * @param memo        备注信息
     */
    public void recordDistinguishSteps(ArrayDeque<String> stepQueue, List<Set<State>> stateSetList, String memo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stateSetList.size(); i++) {
            Set<State> group = stateSetList.get(i);
            if (!group.isEmpty()) {
                // 拼接 "0:{...}\t"
                sb.append(i).append(":").append(setToString(group)).append("\t");
            }
        }

        // 3. 添加备注
        sb.append(":").append(memo);

        // 4. 存入队列并打印
        String resultStr = sb.toString();
        stepQueue.add(resultStr);
//      System.out.println(resultStr);
    }

    /**
     * 展示所有步骤
     *
     * @param stepQueue
     */
    public void showDistinguishSteps(ArrayDeque<String> stepQueue, List<Set<State>> stateSetList) {
        int step = 0;
        System.out.println("GroupSet.size:"+stateSetList.size());
        while (!stepQueue.isEmpty()) {
            String str = stepQueue.poll();
            // 注意：这里去掉了 \r，使用 println 自动换行，避免覆盖显示导致看不清历史
            System.out.println("Step" + step++ + ":\t" + str);
        }
    }

    private String setToString(Set<State> group) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // 将 Set 转为 List 并排序，确保输出总是按 ID 从小到大排列
        // 如果不排序，HashSet 的输出顺序是随机的，会导致测试失败
        List<State> sortedStates = new ArrayList<>(group);
        Collections.sort(sortedStates, Comparator.comparingInt(State::getId));

        for (int i = 0; i < sortedStates.size(); i++) {
            State s = sortedStates.get(i);
            // 拼接 "ID:Type"
            sb.append(s.getId()).append(":").append(s.getType());

            // 逗号分隔符
            if (i < sortedStates.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
