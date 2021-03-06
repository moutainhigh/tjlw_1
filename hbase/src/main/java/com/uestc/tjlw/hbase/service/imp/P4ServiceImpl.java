package com.uestc.tjlw.hbase.service.imp;

import com.uestc.tjlw.common.pojo.P4Info;
import com.uestc.tjlw.hbase.service.HBaseService;
import com.uestc.tjlw.hbase.service.P4Service;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * @author yushansun
 * @title: P4ServiceImpl
 * @projectName tjlw
 * @description: P4业务处理实现类
 * @date 2020/11/44:18 下午
 */
@Service
@Configuration
public class P4ServiceImpl implements P4Service {

    @Autowired
    private HBaseService hBaseService;

    private String  p4TableName="p4Info";    //表格名称




    @Override
    public boolean add2Hbase(P4Info p4Info) {
        //1.添加baseInfo
        hBaseService.putData(p4TableName,p4Info.getRowKey(),P4Info.getBaseInfoFamilyName(),P4Info.getColumns(),p4Info.getValues());
        //2.添加交换机信息
        p4Info.getSwitchList().forEach(aSwitch -> {
            hBaseService.putData(p4TableName,p4Info.getRowKey(),P4Info.getSwitchesFamilyName(),aSwitch.getColumns(),aSwitch.getValues());
        });
        return true;
    }

    @Override
    public boolean createP4InfoTables() {
        return hBaseService.creatTable(p4TableName, Arrays.asList(P4Info.getFamilyNames()));
    }

    @Override
    public P4Info findByTimestamp(String timestamp) {
        Map<String,String> info = hBaseService.getRowData(p4TableName,timestamp);
        P4Info p4Info =  P4Info.getBaseInfoInstance(info);
        if (p4Info==null) return p4Info;
        p4Info.setTimestamp(timestamp);
        return p4Info;
    }

    @Override
    public List<P4Info> findColumnsAndEqualCondition(String[] columns, String[] cmpValues) {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);     //且过滤器集合
        Scan scan = new Scan();
        List<P4Info> p4InfoList = new ArrayList<>();
        if(cmpValues.length!=columns.length) return null; //后续补充异常返回信息
        /*
        通过参数条件创建过滤器
         */
        for (int i=0;i<columns.length;i++){
            String column = columns[i];
            String value = cmpValues[i];
            if(StringUtils.isEmpty(value)) continue;
            Filter filter = hBaseService.singleColumnValueFilter(P4Info.getBaseInfoFamilyName(),column,CompareOperator.EQUAL,value);
            filterList.addFilter(filter);
        }
        scan.setFilter(filterList);
        Map<String,Map<String,String>> map = hBaseService.queryData(p4TableName,scan);
        /*
        将查询结果封装
         */
        map.forEach((k,info)->{
            P4Info p4Info =  P4Info.getBaseInfoInstance(info);
            p4Info.setTimestamp(k);
            p4InfoList.add(p4Info);
        });
        return p4InfoList;
    }

    @Override
    public List<P4Info> findColumnsOrEqualCondition(String[] columns, String[] cmpValues) {
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        Scan scan = new Scan();
        List<P4Info> p4InfoList = new ArrayList<>();
        if(cmpValues.length != columns.length)
            return null;
        for (int i=0; i< columns.length; i++)
        {
            String column = columns[i];
            String value = cmpValues[i];
            if (StringUtils.isEmpty(value))
                continue;
            Filter filter = hBaseService.singleColumnValueFilter(P4Info.getBaseInfoFamilyName(),column,CompareOperator.EQUAL,value);
            filterList.addFilter(filter);
        }
        scan.setFilter(filterList);
        //map承载查询的结果
        Map<String,Map<String,String>> map = hBaseService.queryData(p4TableName,scan);

        //封装查询结果
        map.forEach((k,info)->{
            P4Info p4Info = P4Info.getBaseInfoInstance(info);
            p4Info.setTimestamp(k);
            p4InfoList.add(p4Info);
        });
        return p4InfoList;
    }
    @Override
    public List<P4Info> returnClosestAHundred() throws IOException {
        List<P4Info> P4InfoList = new ArrayList<>();
        List<String> Rowkey = hBaseService.getRowKey("p4Info");
        int rowkeyNumber = Rowkey.size();
        for(int i = rowkeyNumber - 1, j=0; i >=0 ||j < 100; i--){
            P4Info  p4Info = this.findByTimestamp(Rowkey.get(i));
            p4Info.setSwitchList(null);
            P4InfoList.add(p4Info);
            j++;
        }

        return P4InfoList;
    }

    @Override
    public Map responseDDoSdemand(String targetIp,String startRowkey,String endRowkey) throws IOException{
        //创建Map对象承载返回值
        Map response = new HashMap(new HashMap());

    /*
    按照目的IP查出来的所有满足条件的数据包
     */
        Map res1 = hBaseService.getResultScannerQualifierFilter("p4Info",targetIp);
        response.put("所有时间段流向目的IP的数据包",res1);

        //精确起止时间戳
        startRowkey = hBaseService.getClostestRowkey("p4Info",startRowkey);
        endRowkey = hBaseService.getClostestRowkey("p4Info",endRowkey);
        Map res2 = hBaseService.getResultScanner("p4Info",startRowkey,endRowkey);

    /*
    按照起止时间戳得到的数据包
     */
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        Scan scan = new Scan();
        scan.setStartRow(Bytes.toBytes(startRowkey));
        scan.setStopRow(Bytes.toBytes(endRowkey));
        Filter filter = new ValueFilter(CompareFilter.CompareOp.EQUAL, new SubstringComparator(targetIp));
        scan.setFilter(filter);
        Map res3 = hBaseService.queryData("p4Info",scan);
        response.put("按照时间段与目的Ip筛选出的数据包",res3);
        return response;
    };
}
