package inside_payment.service;

import edu.fudan.common.util.Response;
import inside_payment.entity.*;
import inside_payment.repository.AddMoneyRepository;
import inside_payment.repository.PaymentRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Service
public class InsidePaymentServiceImpl implements InsidePaymentService {

    @Autowired
    public AddMoneyRepository addMoneyRepository;

    @Autowired
    public PaymentRepository paymentRepository;

    @Autowired
    public RestTemplate restTemplate;

    @Override
    public Response pay(PaymentInfo info, HttpHeaders headers) {
//        QueryOrderResult result;
        String userId = info.getUserId();


        Response result = new Response();
        if (info.getTripId().startsWith("G") || info.getTripId().startsWith("D")) {
            HttpEntity requestGetOrderResults = new HttpEntity(null, headers);
            ResponseEntity<Response> reGetOrderResults = restTemplate.exchange(
                    "http://ts-order-service:12031/api/v1/orderservice/order/" + info.getOrderId(),
                    HttpMethod.GET,
                    requestGetOrderResults,
                    Response.class);
            result = reGetOrderResults.getBody();

//            result = restTemplate.postForObject("http://ts-order-service:12031/order/getById",getOrderByIdInfo,GetOrderResult.class);
            //result = restTemplate.postForObject(
            //       "http://ts-order-service:12031/order/price", new QueryOrder(info.getOrderId()),QueryOrderResult.class);
        } else {
            HttpEntity requestGetOrderResults = new HttpEntity(null, headers);
            ResponseEntity<Response> reGetOrderResults = restTemplate.exchange(
                    "http://ts-order-other-service:12032/api/v1/orderOtherService/orderOther/" + info.getOrderId(),
                    HttpMethod.GET,
                    requestGetOrderResults,
                    Response.class);
            result = reGetOrderResults.getBody();

//            result = restTemplate.postForObject("http://ts-order-other-service:12032/orderOther/getById",getOrderByIdInfo,GetOrderResult.class);
            //result = restTemplate.postForObject(
            //      "http://ts-order-other-service:12032/orderOther/price", new QueryOrder(info.getOrderId()),QueryOrderResult.class);
        }

        if ("1".equals(result.getStatus())) {
            Order order = (Order) result.getData();
            if (order.getStatus() != OrderStatus.NOTPAID.getCode()) {
                System.out.println("[Inside Payment Service][Pay] Error. Order status Not allowed to Pay.");
                return new Response(0, "Error. Order status Not allowed to Pay.", null);
            }

            Payment payment = new Payment();
            payment.setOrderId(info.getOrderId());
            payment.setPrice(order.getPrice());
            payment.setUserId(userId);

            //判断一下账户余额够不够，不够要去站外支付
            List<Payment> payments = paymentRepository.findByUserId(userId);
            List<Money> addMonies = addMoneyRepository.findByUserId(userId);
            Iterator<Payment> paymentsIterator = payments.iterator();
            Iterator<Money> addMoniesIterator = addMonies.iterator();

            BigDecimal totalExpand = new BigDecimal("0");
            while (paymentsIterator.hasNext()) {
                Payment p = paymentsIterator.next();
                totalExpand = totalExpand.add(new BigDecimal(p.getPrice()));
            }
            totalExpand = totalExpand.add(new BigDecimal(order.getPrice()));

            BigDecimal money = new BigDecimal("0");
            while (addMoniesIterator.hasNext()) {
                Money addMoney = addMoniesIterator.next();
                money = money.add(new BigDecimal(addMoney.getMoney()));
            }

            if (totalExpand.compareTo(money) > 0) {
                //站外支付
                Payment outsidePaymentInfo = new Payment();
                outsidePaymentInfo.setOrderId(info.getOrderId());
                outsidePaymentInfo.setUserId(userId);
                outsidePaymentInfo.setPrice(order.getPrice());

                /****这里调用第三方支付***/

                HttpEntity requestEntityOutsidePaySuccess = new HttpEntity(outsidePaymentInfo, headers);
                ResponseEntity<Response> reOutsidePaySuccess = restTemplate.exchange(
                        "http://ts-payment-service:19001/api/v1/paymentservice/payment",
                        HttpMethod.POST,
                        requestEntityOutsidePaySuccess,
                        Response.class);
                Response outsidePaySuccess = reOutsidePaySuccess.getBody();

//                boolean outsidePaySuccess = restTemplate.postForObject(
//                        "http://ts-payment-service:19001/payment/pay", outsidePaymentInfo,Boolean.class);
//                boolean outsidePaySuccess = false;
//                try{
//                    System.out.println("[Payment Service][Turn To Outside Patment] Async Task Begin");
//                    Future<Boolean> task = asyncTask.sendAsyncCallToPaymentService(outsidePaymentInfo);
//                    outsidePaySuccess = task.get(2000,TimeUnit.MILLISECONDS).booleanValue();
//
//                }catch (Exception e){
//                    System.out.println("[Inside Payment][Turn to Outside Payment] Time Out.");
//                    //e.printStackTrace();
//                    return false;
//                }

                if ("1".equals(outsidePaySuccess.getMsg())) {
                    payment.setType(PaymentType.O);
                    paymentRepository.save(payment);
                    setOrderStatus(info.getTripId(), info.getOrderId(), headers);
                    return new Response(1, "Payment Success", null);
                } else {
                    return new Response(0, "Payment Failed", null);
                }
            } else {
                setOrderStatus(info.getTripId(), info.getOrderId(), headers);
                payment.setType(PaymentType.P);
                paymentRepository.save(payment);
            }
            return new Response(1, "Payment Success", null);

        } else {
            return new Response(0, "Payment Failed, Order Not Exists", null);
        }
    }

    @Override
    public Response createAccount(AccountInfo info, HttpHeaders headers) {
        List<Money> list = addMoneyRepository.findByUserId(info.getUserId());
        if (list.size() == 0) {
            Money addMoney = new Money();
            addMoney.setMoney(info.getMoney());
            addMoney.setUserId(info.getUserId());
            addMoney.setType(MoneyType.A);
            addMoneyRepository.save(addMoney);
            return new Response(1, "Create Account Success", null);
        } else {
            return new Response(0, "Create Account Failed", null);
        }
    }

    @Override
    public Response addMoney(String userId, String money, HttpHeaders headers) {
        if (addMoneyRepository.findByUserId(userId) != null) {
            Money addMoney = new Money();
            addMoney.setUserId(userId);
            addMoney.setMoney(money);
            addMoney.setType(MoneyType.A);
            addMoneyRepository.save(addMoney);
            return new Response(1, "Add Money Success", null);
        } else {
            return new Response(0, "Add Money Failed", null);
        }
    }

    @Override
    public Response queryAccount(HttpHeaders headers) {
        List<Balance> result = new ArrayList<Balance>();
        List<Money> list = addMoneyRepository.findAll();
        Iterator<Money> ite = list.iterator();
        HashMap<String, String> map = new HashMap<String, String>();
        while (ite.hasNext()) {
            Money addMoney = ite.next();
            if (map.containsKey(addMoney.getUserId())) {
                BigDecimal money = new BigDecimal(map.get(addMoney.getUserId()));
                map.put(addMoney.getUserId(), money.add(new BigDecimal(addMoney.getMoney())).toString());
            } else {
                map.put(addMoney.getUserId(), addMoney.getMoney());
            }
        }

        Iterator ite1 = map.entrySet().iterator();
        while (ite1.hasNext()) {
            Map.Entry entry = (Map.Entry) ite1.next();
            String userId = (String) entry.getKey();
            String money = (String) entry.getValue();

            List<Payment> payments = paymentRepository.findByUserId(userId);
            Iterator<Payment> iterator = payments.iterator();
            String totalExpand = "0";
            while (iterator.hasNext()) {
                Payment p = iterator.next();
                BigDecimal expand = new BigDecimal(totalExpand);
                totalExpand = expand.add(new BigDecimal(p.getPrice())).toString();
            }
            String balanceMoney = new BigDecimal(money).subtract(new BigDecimal(totalExpand)).toString();
            Balance balance = new Balance();
            balance.setUserId(userId);
            balance.setBalance(balanceMoney);
            result.add(balance);
        }

        return new Response(1, "Success", result);
    }

    public String queryAccount(String userId, HttpHeaders headers) {
        List<Payment> payments = paymentRepository.findByUserId(userId);
        List<Money> addMonies = addMoneyRepository.findByUserId(userId);
        Iterator<Payment> paymentsIterator = payments.iterator();
        Iterator<Money> addMoniesIterator = addMonies.iterator();

        BigDecimal totalExpand = new BigDecimal("0");
        while (paymentsIterator.hasNext()) {
            Payment p = paymentsIterator.next();
            totalExpand.add(new BigDecimal(p.getPrice()));
        }

        BigDecimal money = new BigDecimal("0");
        while (addMoniesIterator.hasNext()) {
            Money addMoney = addMoniesIterator.next();
            money.add(new BigDecimal(addMoney.getMoney()));
        }

        String result = money.subtract(totalExpand).toString();
        return result;
    }

    @Override
    public Response queryPayment(HttpHeaders headers) {
        List<Payment> payments = paymentRepository.findAll();
        if (payments != null && payments.size() > 0)
            return new Response(1, "Query Payment Success", payments);
        else
            return new Response(0, "Query Payment Failed", null);
    }

    @Override
    public Response drawBack(String userId, String money, HttpHeaders headers) {
        if (addMoneyRepository.findByUserId(userId) != null) {
            Money addMoney = new Money();
            addMoney.setUserId(userId);
            addMoney.setMoney(money);
            addMoney.setType(MoneyType.D);
            addMoneyRepository.save(addMoney);
            return new Response(1, "Draw Back Money Scuuess", null);
        } else {
            return new Response(0, "Draw Back Money Failed", null);
        }
    }

    @Override
    public Response payDifference(PaymentInfo info, HttpHeaders headers) {

        String userId = info.getUserId();

        Payment payment = new Payment();
        payment.setOrderId(info.getOrderId());
        payment.setPrice(info.getPrice());
        payment.setUserId(info.getUserId());


        List<Payment> payments = paymentRepository.findByUserId(userId);
        List<Money> addMonies = addMoneyRepository.findByUserId(userId);
        Iterator<Payment> paymentsIterator = payments.iterator();
        Iterator<Money> addMoniesIterator = addMonies.iterator();

        BigDecimal totalExpand = new BigDecimal("0");
        while (paymentsIterator.hasNext()) {
            Payment p = paymentsIterator.next();
            totalExpand.add(new BigDecimal(p.getPrice()));
        }
        totalExpand.add(new BigDecimal(info.getPrice()));

        BigDecimal money = new BigDecimal("0");
        while (addMoniesIterator.hasNext()) {
            Money addMoney = addMoniesIterator.next();
            money.add(new BigDecimal(addMoney.getMoney()));
        }

        if (totalExpand.compareTo(money) > 0) {
            //站外支付
            Payment outsidePaymentInfo = new Payment();
            outsidePaymentInfo.setOrderId(info.getOrderId());
            outsidePaymentInfo.setUserId(userId);
            outsidePaymentInfo.setPrice(info.getPrice());

            HttpEntity requestEntityOutsidePaySuccess = new HttpEntity(outsidePaymentInfo, headers);
            ResponseEntity<Response> reOutsidePaySuccess = restTemplate.exchange(
                    "http://ts-payment-service:19001/api/v1/paymentservice/payment",
                    HttpMethod.POST,
                    requestEntityOutsidePaySuccess,
                    Response.class);
            Response outsidePaySuccess = reOutsidePaySuccess.getBody();

//            boolean outsidePaySuccess = restTemplate.postForObject(
//                    "http://ts-payment-service:19001/payment/pay", outsidePaymentInfo,Boolean.class);
            if ("1".equals(outsidePaySuccess.getStatus())) {
                payment.setType(PaymentType.E);
                paymentRepository.save(payment);
                return new Response(1, "Pay Difference Success", null);
            } else {
                return new Response(0, "Pay Difference Failed", null);
            }
        } else {
            payment.setType(PaymentType.E);
            paymentRepository.save(payment);
        }
        return new Response(1, "Pay Difference Success", null);
    }

    @Override
    public Response queryAddMoney(HttpHeaders headers) {
        List<Money> monies = addMoneyRepository.findAll();
        if (monies != null && monies.size() > 0) {
            return new Response(1, "Query Money Success", null);
        } else {
            return new Response(0, "", null);
        }
    }

    private Response setOrderStatus(String tripId, String orderId, HttpHeaders headers) {

        int orderStatus = 1;//order paid and not collected
        Response result;
        if (tripId.startsWith("G") || tripId.startsWith("D")) {

            HttpEntity requestEntityModifyOrderStatusResult = new HttpEntity(null, headers);
            ResponseEntity<Response> reModifyOrderStatusResult = restTemplate.exchange(
                    "http://ts-order-service:12031/api/v1/orderservice/order/modifyOrderStatus/" + orderId + "/" + orderStatus,
                    HttpMethod.GET,
                    requestEntityModifyOrderStatusResult,
                    Response.class);
            result = reModifyOrderStatusResult.getBody();

//            result = restTemplate.postForObject(
//                    "http://ts-order-service:12031/order/modifyOrderStatus", info, ModifyOrderStatusResult.class);
        } else {
            HttpEntity requestEntityModifyOrderStatusResult = new HttpEntity(null, headers);
            ResponseEntity<Response> reModifyOrderStatusResult = restTemplate.exchange(
                    "http://ts-order-other-service:12032/api/v1/orderOtherService/orderOther/modifyOrderStatus/" + orderId + "/" + orderStatus,
                    HttpMethod.GET,
                    requestEntityModifyOrderStatusResult,
                    Response.class);
            result = reModifyOrderStatusResult.getBody();

//            result = restTemplate.postForObject(
//                    "http://ts-order-other-service:12032/orderOther/modifyOrderStatus", info, ModifyOrderStatusResult.class);
        }
        return result;
    }

    @Override
    public void initPayment(Payment payment, HttpHeaders headers) {
        Payment paymentTemp = paymentRepository.findById(payment.getId());
        if (paymentTemp == null) {
            paymentRepository.save(payment);
        } else {
            System.out.println("[Inside Payment Service][Init Payment] Already Exists:" + payment.getId());
        }
    }

//    private boolean sendOrderCreateEmail(){
//        result = restTemplate.postForObject(
//                "http://ts-notification-service:12031/order/modifyOrderStatus", info, ModifyOrderStatusResult.class);
//        return true;
//    }
}
