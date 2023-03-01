package drz.tmdb.Transaction.Transactions;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import drz.tmdb.Memory.Tuple;
import drz.tmdb.Memory.TupleList;

public class Where {
    Formula formula=new Formula();
    public SelectResult where(PlainSelect plainSelect,SelectResult selectResult){
        execute(plainSelect.getWhere(),selectResult);

        return selectResult;
    }

    //核心类，将整个where语法树进行后续遍历
    public SelectResult execute(Expression expression,SelectResult selectResult){
        SelectResult res=new SelectResult();
        if(selectResult.tpl.tuplelist.isEmpty()) return selectResult;
        String a=expression.getClass().getSimpleName();
        switch (expression.getClass().getSimpleName()){
            case "OrExpression": res=orExpression((OrExpression) expression,selectResult); break;
            case "AndExpression": res=andExpression((AndExpression) expression,selectResult); break;
            case "InExpression": res=inExpression((InExpression) expression,selectResult); break;
            case "EqualsTo": res=equalsToExpression((EqualsTo) expression,selectResult); break;
            case "MinorThan": res=minorThan((MinorThan) expression,selectResult); break;
            case "GreaterThan": res=greaterThan((GreaterThan) expression,selectResult); break;
        }
        return res;
    }


    public SelectResult andExpression(AndExpression expression, SelectResult selectResult){
        Expression left=expression.getLeftExpression();
        Expression right=expression.getRightExpression();
        SelectResult selectResult1=execute(left,selectResult);
        SelectResult selectResult2=execute(right,selectResult);
        HashSet<Tuple> selectResultSet1=getTupleSet(selectResult1);
        HashSet<Tuple> selectResultSet2=getTupleSet(selectResult2);
        HashSet<Tuple> overlap=new HashSet<>();
        //将两个条件都满足的Tuple加入overlap中
        for(Tuple tuple:selectResultSet2){
            if(selectResultSet1.contains(tuple)) overlap.add(tuple);
        }
        return getSelectResultFromSet(selectResult,overlap);
    }

    public SelectResult orExpression(OrExpression expression,SelectResult selectResult){
        Expression left=expression.getLeftExpression();
        Expression right=expression.getRightExpression();
        SelectResult selectResult1=execute(left,selectResult);
        SelectResult selectResult2=execute(right,selectResult);
        HashSet<Tuple> selectResultSet1=getTupleSet(selectResult1);
        HashSet<Tuple> selectResultSet2=getTupleSet(selectResult2);
        //将selectResultSet2中tuple加入selectResultSet1中，这里将selectResultSet1作为结果集合
        for(Tuple tuple:selectResultSet2){
            selectResultSet1.add(tuple);
        }
        return getSelectResultFromSet(selectResult,selectResultSet1);
    }

    public SelectResult inExpression(InExpression expression, SelectResult selectResult){
        ArrayList<Object> left=formula.formulaExecute(expression.getLeftExpression(),selectResult);
        List<Object> right=new ArrayList<>();
        //in表达式右边可能是一个list
        if(expression.getRightItemsList()!=null){
            for(Expression expression1:((ExpressionList)expression.getRightItemsList()).getExpressions()){
                ArrayList<Object> temp2=formula.formulaExecute(expression1,selectResult);
                right.add(transType(temp2.get(0)));
            }
        }
        //in表达式的右边可能是一个SubSelect
        else if(expression.getRightExpression().getClass().getSimpleName().equals("SubSelect")){
            SelectResult temp=(new Select()).select(expression.getRightExpression());
            for(int i=0;i<temp.tpl.tuplelist.size();i++){
                right.add(transType(temp.tpl.tuplelist.get(i).tuple[0]));
            }
        }
        ArrayList<Tuple> resTuple=new ArrayList<>();
        //最后，如果left存在于right的集合中，就加入到结果集合
        for(int i=0;i<left.size();i++){
            if(right.contains(transType(left.get(i)))) resTuple.add(selectResult.tpl.tuplelist.get(i));
        }
        selectResult.tpl.tuplelist=resTuple;
        return selectResult;
    }

    public SelectResult equalsToExpression(EqualsTo expression,SelectResult selectResult){
        ArrayList<Object> left=formula.formulaExecute(expression.getLeftExpression(),selectResult);
        ArrayList<Object> right=formula.formulaExecute(expression.getRightExpression(),selectResult);
        HashSet<Tuple> set=new HashSet<>();
        for(int i=0;i<left.size();i++){
            String tempLeft=transType(left.get(i));
            String tempRight=transType(right.get(i));
            //左边和右边相等则加入结果集合。
            if(tempLeft.equals(tempRight)) set.add(selectResult.tpl.tuplelist.get(i));
        }
        return getSelectResultFromSet(selectResult,set);
    }

    public SelectResult minorThan(MinorThan expression,SelectResult selectResult){
        ArrayList<Object> left=formula.formulaExecute(expression.getLeftExpression(),selectResult);
        ArrayList<Object> right=formula.formulaExecute(expression.getRightExpression(),selectResult);
        HashSet<Tuple> set=new HashSet<>();
        for(int i=0;i<left.size();i++){
            String tempLeft=transType(left.get(i));
            String tempRight=transType(right.get(i));
            //左边小于右边，则加入结果集合
            if(tempLeft.compareTo(tempRight)<0) set.add(selectResult.tpl.tuplelist.get(i));
        }
        return getSelectResultFromSet(selectResult,set);
    }

    public SelectResult greaterThan(GreaterThan expression,SelectResult selectResult){
        ArrayList<Object> left=formula.formulaExecute(expression.getLeftExpression(),selectResult);
        ArrayList<Object> right=formula.formulaExecute(expression.getRightExpression(),selectResult);
        HashSet<Tuple> set=new HashSet<>();
        for(int i=0;i<left.size();i++){
            String tempLeft=transType(left.get(i));
            String tempRight=transType(right.get(i));
            //左边大于右边，则加入结果集合
            if(tempLeft.compareTo(tempRight)>0) set.add(selectResult.tpl.tuplelist.get(i));
        }
        return getSelectResultFromSet(selectResult,set);
    }
    

    public HashSet<Tuple> getTupleSet(SelectResult selectResult){
        HashSet<Tuple> set=new HashSet<>();
        for(Tuple tuple:selectResult.tpl.tuplelist){
            set.add(tuple);
        }
        return set;
    }

    public SelectResult getSelectResultFromSet(SelectResult selectResult,HashSet<Tuple> set){
        TupleList tupleList=new TupleList();
        for(Tuple tuple:set){
            tupleList.addTuple(tuple);
        }
        selectResult.tpl=tupleList;
        return selectResult;
    }

    //进行类型转换，很多时候需要使用
    public String transType(Object obj){
        switch(obj.getClass().getSimpleName()){
            case "String":
                boolean flag=false;
                try{
                    Double temp=Double.parseDouble(String.valueOf(obj));
                    flag=true;
                }
                catch(Throwable throwable){}
                if(flag==true) return String.valueOf(Double.parseDouble(String.valueOf(obj)));
                else return (String)obj;
            case "Float": return String.valueOf((double) obj);
            case "Double": return String.valueOf(obj);
            case "Integer": return String.valueOf((double) obj);
            case "Long": return String.valueOf((double) obj);
            case "Character": return String.valueOf(obj);
            case "Short": return String.valueOf((double) obj);
            default: return "";
        }
    }
}
