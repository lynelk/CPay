package net.citotech.cito.vending;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing H5/QR vending flow. No merchant/admin session is required. */
@RestController
public class VendingHostedRentalController {
    private final VendingHostedRentalService hosted;

    public VendingHostedRentalController(VendingHostedRentalService hosted) {
        this.hosted = hosted;
    }

    @GetMapping(path = "/api/v2/vending/hosted/stations/{publicToken}")
    public ResponseEntity<?> station(@PathVariable("publicToken") String publicToken) {
        try {
            return ResponseEntity.ok(hosted.publicDevice(publicToken));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping(
            path = "/api/v2/vending/hosted/stations/{publicToken}/start",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> start(
            @PathVariable("publicToken") String publicToken,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            String msisdn = text(body.get("customerMsisdn"));
            String channel = text(body.get("channel"));
            return ResponseEntity.accepted()
                    .body(hosted.start(publicToken, msisdn, channel, clientIp(request)));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to start vending rental");
        }
    }

    @GetMapping(path = "/api/v2/vending/hosted/sessions/{statusToken}")
    public ResponseEntity<?> status(@PathVariable("statusToken") String statusToken) {
        try {
            return ResponseEntity.ok(hosted.status(statusToken));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping(path = "/vending/rent/{publicToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable("publicToken") String publicToken) {
        String token = js(publicToken);
        String html =
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                  <meta name="color-scheme" content="light dark">
                  <title>CPay Power Rental</title>
                  <style>
                    :root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#102235;background:#eef4f7}
                    *{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at top,#dff7ff 0,#eef4f7 42%,#f7f9fa 100%);padding:18px}
                    .shell{max-width:520px;margin:3vh auto}.brand{font-size:14px;font-weight:800;letter-spacing:.12em;color:#057e9f;text-transform:uppercase}
                    .card{background:rgba(255,255,255,.94);border:1px solid #d9e5ea;border-radius:24px;box-shadow:0 24px 60px rgba(15,43,60,.13);padding:24px;margin-top:14px}
                    h1{font-size:28px;line-height:1.1;margin:8px 0}.muted{color:#607483}.price{font-size:30px;font-weight:800;margin:14px 0}
                    .facts{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:18px 0}.fact{background:#f3f8fa;border-radius:14px;padding:12px}.fact b{display:block;margin-top:4px}
                    label{font-size:13px;font-weight:700;display:block;margin:14px 0 7px}input,select,button{width:100%;height:50px;border-radius:13px;font:inherit}
                    input,select{border:1px solid #c8d8df;background:white;color:#102235;padding:0 13px}button{border:0;background:#0797bd;color:white;font-weight:800;margin-top:18px;cursor:pointer}
                    button:disabled{opacity:.55;cursor:not-allowed}.status{margin-top:16px;padding:14px;border-radius:14px;background:#eef7fb;display:none}.status.show{display:block}.error{background:#fff0f0;color:#8b2626}.ok{background:#edf9f1;color:#17643a}
                    .fine{font-size:12px;color:#718591;margin-top:16px;line-height:1.45}@media(prefers-color-scheme:dark){:root{color:#eaf3f7;background:#0d161c}body{background:#0d161c}.card{background:#13222b;border-color:#263a46}.muted,.fine{color:#9fb2bd}.fact{background:#172b36}input,select{background:#0f1c23;color:#eaf3f7;border-color:#36505f}}
                  </style>
                </head>
                <body>
                  <main class="shell">
                    <div class="brand">CPay · Vending</div>
                    <section class="card">
                      <div id="loading">Loading station…</div>
                      <div id="content" hidden>
                        <div class="muted" id="location"></div>
                        <h1>Rent a power bank</h1>
                        <div class="price"><span id="currency"></span> <span id="deposit"></span> deposit</div>
                        <div class="facts">
                          <div class="fact"><span class="muted">Rate</span><b id="rate"></b></div>
                          <div class="fact"><span class="muted">Available now</span><b id="available"></b></div>
                        </div>
                        <form id="rentalForm">
                          <label for="msisdn">Mobile money number</label>
                          <input id="msisdn" name="msisdn" inputmode="tel" autocomplete="tel" placeholder="2567…" required>
                          <label for="channel">Payment network</label>
                          <select id="channel" name="channel">
                            <option value="">Automatic routing</option>
                            <option value="mtn_momo">MTN MoMo</option>
                            <option value="airtel_money">Airtel Money</option>
                            <option value="airtel_open_api">Airtel OpenAPI</option>
                          </select>
                          <button id="submit" type="submit">Pay deposit & rent</button>
                        </form>
                        <div id="status" class="status" role="status"></div>
                        <p class="fine">Your deposit is collected through CPay. The station releases an item only after the payment reaches a successful state. Any eligible refund is sent back through CPay after return.</p>
                      </div>
                    </section>
                  </main>
                  <script>
                    const token = "__CPAY_VENDING_TOKEN__";
                    const loading = document.getElementById('loading');
                    const content = document.getElementById('content');
                    const statusBox = document.getElementById('status');
                    const submit = document.getElementById('submit');
                    let statusToken = null;
                    const showStatus = (text, cls='') => { statusBox.textContent=text; statusBox.className='status show '+cls; };
                    async function loadStation(){
                      const r=await fetch('/api/v2/vending/hosted/stations/'+encodeURIComponent(token));
                      const d=await r.json(); if(!r.ok) throw new Error(d.message||'Station unavailable');
                      document.getElementById('location').textContent=(d.locationName||'Vending station')+(d.locationAddress?' · '+d.locationAddress:'');
                      document.getElementById('currency').textContent=d.currency||''; document.getElementById('deposit').textContent=d.depositAmount||'0';
                      document.getElementById('rate').textContent=(d.currency||'')+' '+(d.unitPrice||'0')+' / '+(d.billingBlockMinutes||60)+' min';
                      document.getElementById('available').textContent=String(d.availableCount??0);
                      if(d.status!=='ONLINE'||Number(d.availableCount||0)<=0){ submit.disabled=true; showStatus('This station is currently unavailable.','error'); }
                      loading.hidden=true; content.hidden=false;
                    }
                    document.getElementById('rentalForm').addEventListener('submit',async(e)=>{
                      e.preventDefault(); submit.disabled=true; showStatus('Sending the deposit request to your phone…');
                      try{
                        const r=await fetch('/api/v2/vending/hosted/stations/'+encodeURIComponent(token)+'/start',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({customerMsisdn:document.getElementById('msisdn').value,channel:document.getElementById('channel').value})});
                        const d=await r.json(); if(!r.ok) throw new Error(d.message||'Unable to start rental'); statusToken=d.statusToken; showStatus('Approve the mobile money request on your phone. We will release the power bank after confirmation.'); poll();
                      }catch(err){ showStatus(err.message||'Unable to start rental','error'); submit.disabled=false; }
                    });
                    async function poll(){
                      if(!statusToken)return;
                      try{
                        const r=await fetch('/api/v2/vending/hosted/sessions/'+encodeURIComponent(statusToken)); const d=await r.json(); if(!r.ok) throw new Error(d.message||'Status unavailable');
                        const s=String(d.status||'');
                        if(s==='ACTIVE'){showStatus('Payment confirmed. Your power bank has been released.','ok');return;}
                        if(['PAYMENT_FAILED','RELEASE_FAILED','REFUND_FAILED'].includes(s)){showStatus('Rental needs attention: '+s.replaceAll('_',' ').toLowerCase()+'.','error');submit.disabled=false;return;}
                        if(s==='SETTLED'){showStatus('Rental settled. Thank you.','ok');return;}
                        showStatus(s==='READY_TO_RELEASE'?'Payment confirmed. Releasing your power bank…':'Waiting for payment confirmation…');
                        setTimeout(poll,3000);
                      }catch(err){showStatus(err.message||'Status check failed','error');setTimeout(poll,6000);}
                    }
                    loadStation().catch(err=>{loading.textContent=err.message||'Station unavailable';});
                  </script>
                </body></html>
                """
                        .replace("__CPAY_VENDING_TOKEN__", token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(
                        Map.of(
                                "code",
                                "VENDING_HOSTED_REJECTED",
                                "message",
                                message == null ? "Request rejected" : message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String js(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
