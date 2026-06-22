package com.tao.autobook.parser

import com.tao.autobook.data.BuiltInCategories
import com.tao.autobook.data.MerchantRuleEntity
import com.tao.autobook.data.PaymentApp

class CategoryClassifier {
    private val keywordMap = mapOf(
        BuiltInCategories.FOOD to listOf(
            "餐", "餐费", "餐饮", "咖啡", "奶茶", "饭", "面", "火锅", "烧烤",
            "外卖", "快餐", "甜品", "蛋糕", "面包", "早餐", "午餐", "晚餐",
            "麦当劳", "肯德基", "瑞幸", "星巴克", "喜茶", "蜜雪", "霸王茶姬",
            "美团外卖", "饿了么", "食堂", "小吃", "零食", "水果", "生鲜",
            "牛小匠", "鸭头", "奶茶", "茶百道", "古茗", "沪上阿姨"
        ),
        BuiltInCategories.TRANSPORT to listOf(
            "地铁", "公交", "滴滴", "打车", "网约车", "出租车", "停车", "加油",
            "充电", "高速", "高德", "铁路", "12306", "航旅", "机票", "火车",
            "汽车票", "单车", "哈啰", "青桔", "美团单车", "充电桩",
            "加油卡", "ETC", "过路费", "代驾", "租车"
        ),
        BuiltInCategories.SHOPPING to listOf(
            "超市", "便利店", "京东", "淘宝", "天猫", "拼多多", "抖音商城",
            "小红书", "商场", "服饰", "衣服", "鞋", "数码", "手机", "家电",
            "快递", "菜鸟", "顺丰", "优衣库", "盒马", "山姆", "沃尔玛",
            "大润发", "永辉", "苏宁", "国美", "名创优品", "宜家",
            "白条", "花呗", "先用后付", "社区团购", "多多买菜"
        ),
        BuiltInCategories.BILLS to listOf(
            "电费", "水费", "燃气", "话费", "宽带", "物业", "充值", "流量",
            "有线", "停车月卡", "生活缴费", "暖气费", "供暖", "煤气",
            "手机充值", "流量包", "话费充值", "电费缴纳", "水费缴纳"
        ),
        BuiltInCategories.ENTERTAINMENT to listOf(
            "电影", "影院", "游戏", "会员", "视频", "音乐", "演出", "剧场",
            "门票", "旅游", "酒店", "民宿", "携程", "飞猪", "去哪儿",
            "爱奇艺", "腾讯视频", "优酷", "网易云", "B站", "bilibili",
            "Steam", "Switch", "PlayStation", "网吧", "KTV", "剧本杀"
        ),
        BuiltInCategories.MEDICAL to listOf(
            "医院", "药", "诊所", "医保", "体检", "挂号", "医药", "药房",
            "美团买药", "叮当快药", "口腔", "牙科", "眼科", "防疫",
            "核酸检测", "疫苗", "保健", "养生", "中医"
        ),
        BuiltInCategories.EDUCATION to listOf(
            "课程", "培训", "学校", "教育", "书店", "教材", "学费",
            "得到", "知识", "考试", "辅导", "家教", "考研", "公考",
            "英语", "编程", "网课", "慕课", "学堂"
        ),
        BuiltInCategories.TRANSFER to listOf(
            "转账", "红包", "还款", "收款", "付款给个人", "零钱通",
            "余额宝", "银行卡转入", "银行卡转出", "提现"
        ),
        BuiltInCategories.SOCIAL to listOf(
            "礼", "份子", "婚礼", "生日", "请客", "聚会", "红包",
            "随礼", "人情", "送礼", "乔迁", "满月", "升学"
        )
    )

    fun classify(merchant: String, rawText: String, rules: List<MerchantRuleEntity> = emptyList(), app: PaymentApp = PaymentApp.UNKNOWN): String {
        val haystack = merchant + " " + rawText
        rules.firstOrNull { rule ->
            haystack.contains(rule.keyword, ignoreCase = true) && (rule.paymentApp == PaymentApp.UNKNOWN || rule.paymentApp == app)
        }?.let { return it.categoryId }

        keywordMap.entries.firstOrNull { (_, keywords) -> keywords.any { haystack.contains(it, ignoreCase = true) } }
            ?.let { return it.key }
        return BuiltInCategories.OTHER
    }
}
