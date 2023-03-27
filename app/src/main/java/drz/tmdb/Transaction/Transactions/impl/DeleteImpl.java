package drz.tmdb.Transaction.Transactions.impl;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import drz.tmdb.Memory.SystemTable.BiPointerTableItem;
import drz.tmdb.Memory.SystemTable.ClassTableItem;
import drz.tmdb.Memory.SystemTable.ObjectTableItem;
import drz.tmdb.Memory.Tuple;
import drz.tmdb.Transaction.Transactions.Exception.TMDBException;
import drz.tmdb.Transaction.Transactions.Delete;
import drz.tmdb.Transaction.Transactions.Select;
import drz.tmdb.Transaction.Transactions.utils.MemConnect;
import drz.tmdb.Transaction.Transactions.utils.SelectResult;

public class DeleteImpl implements Delete {

    private MemConnect memConnect;

    public DeleteImpl(MemConnect memConnect) {
        this.memConnect = memConnect;
    }

    public DeleteImpl() {
    }

    public ArrayList<Integer> delete(Statement statement) throws JSQLParserException, TMDBException {
        return execute((net.sf.jsqlparser.statement.delete.Delete) statement);
    }

    public ArrayList<Integer> execute(net.sf.jsqlparser.statement.delete.Delete delete) throws JSQLParserException, TMDBException {
        //获取需要删除的表名
        Table table = delete.getTable();
        //获取delete中的where表达式
        Expression where = delete.getWhere();
        String sql="select * from " + table + " where " + where.toString() + ";";
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sql.getBytes());
        net.sf.jsqlparser.statement.select.Select parse = (net.sf.jsqlparser.statement.select.Select) CCJSqlParserUtil.parse(byteArrayInputStream);
        Select select=new SelectImpl();
        SelectResult selectResult = select.select(parse);
        MemConnect memConnect=new MemConnect();
        ArrayList<Integer> integers = new ArrayList<>();
        for(Tuple tuple:selectResult.getTpl().tuplelist){
            memConnect.DeleteTuple(tuple.getTupleId());
            integers.add(tuple.getTupleId());
        }
        return integers;
//        String[] p=new String[5];
//        p[0]="-1";
//        p[1]=table.getName();
//        //获取表达式的形式
//        String temp=where.getClass().getSimpleName();
//        switch (temp){
//            case "EqualsTo" ://等于的处理
//                EqualsTo equals=(EqualsTo) where;
//                p[2]=equals.getLeftExpression().toString();
//                p[3]="=";
//                p[4]=equals.getRightExpression().toString();
//                break;
//            case "GreaterThan" ://大于的处理
//                GreaterThan greaterThan =(GreaterThan) where;
//                p[2]=greaterThan.getLeftExpression().toString();
//                p[3]=">";
//                p[4]=greaterThan.getRightExpression().toString();
//                break;
//            case "MinorThan" ://小于的处理
//                MinorThan minorThan =(MinorThan) where;
//                p[2]=minorThan.getLeftExpression().toString();
//                p[3]=">";
//                p[4]=minorThan.getRightExpression().toString();
//                break;
//            default:
//                break;
//        }
//        return new MemConnect().delete(p);
    }

    public ArrayList<Integer> delete(String[] p) {
        String classname = p[1];
        String attrname = p[2];
        int classid = 0;
        int attrid=0;
        String attrtype=null;
        for (ClassTableItem item: memConnect.getClasst().classTable) {
            if (item.classname.equals(classname) && item.attrname.equals(attrname)) {
                classid = item.classid;
                attrid = item.attrid;
                attrtype = item.attrtype;
                break;
            }
        }
        //寻找需要删除的
        MemConnect.OandB ob2 = new MemConnect.OandB();
        for (Iterator it1 = memConnect.getTopt().objectTable.iterator(); it1.hasNext();){
            ObjectTableItem item = (ObjectTableItem)it1.next();
            if(item.classid == classid){
                Tuple tuple = memConnect.GetTuple(item.tupleid);
                if(memConnect.Condition(attrtype,tuple,attrid,p[4])){
                    //需要删除的元组
                    MemConnect.OandB ob =new MemConnect.OandB(DeletebyID(item.tupleid));
                    for(ObjectTableItem obj:ob.o){
                        ob2.o.add(obj);
                    }
                    for(BiPointerTableItem bip:ob.b){
                        ob2.b.add(bip);
                    }

                }
            }
        }
        ArrayList<Integer> integers = new ArrayList<>();
        for(ObjectTableItem obj:ob2.o){
            integers.add(obj.tupleid);
            memConnect.getTopt().objectTable.remove(obj);
        }
        for(BiPointerTableItem bip:ob2.b) {
            memConnect.getBiPointerT().biPointerTable.remove(bip);
        }
        return integers;
    }

    MemConnect.OandB DeletebyID(int id){

        List<ObjectTableItem> todelete1 = new ArrayList<>();
        List<BiPointerTableItem>todelete2 = new ArrayList<>();
        MemConnect.OandB ob = new MemConnect.OandB(todelete1,todelete2);
        for (Iterator it1 = memConnect.getTopt().objectTable.iterator(); it1.hasNext();){
            ObjectTableItem item  = (ObjectTableItem)it1.next();
            if(item.tupleid == id){
                //需要删除的tuple


                //删除代理类的元组
                int deobid = 0;

                for(Iterator it = memConnect.getBiPointerT().biPointerTable.iterator(); it.hasNext();){
                    BiPointerTableItem item1 =(BiPointerTableItem) it.next();
                    if(item.tupleid == item1.deputyobjectid){
                        //it.remove();
                        if(!todelete2.contains(item1))
                            todelete2.add(item1);
                    }
                    if(item.tupleid == item1.objectid){
                        deobid = item1.deputyobjectid;
                        MemConnect.OandB ob2=new MemConnect.OandB(DeletebyID(deobid));

                        for(ObjectTableItem obj:ob2.o){
                            if(!todelete1.contains(obj))
                                todelete1.add(obj);
                        }
                        for(BiPointerTableItem bip:ob2.b){
                            if(!todelete2.contains(bip))
                                todelete2.add(bip);
                        }

                        //biPointerT.biPointerTable.remove(item1);

                    }
                }


                //删除自身
                memConnect.DeleteTuple(item.tupleid);
                if(!todelete2.contains(item));
                todelete1.add(item);

            }
        }

        return ob;
    }
}