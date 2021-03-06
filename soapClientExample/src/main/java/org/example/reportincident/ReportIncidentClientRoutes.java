package org.example.reportincident;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.transport.http.HTTPException;

public class ReportIncidentClientRoutes extends RouteBuilder {

	private static final String URL_PART_A = "http://localhost:8181/cxf/camel-example-cxf-osgi/webservices/incidentA";
	private static final String URL_PART_B = "http://localhost:8181/cxf/camel-example-cxf-osgi/webservices/incidentB";
	public static final String SERVICE_CLASS = "org.apache.camel.example.reportincident.ReportIncidentEndpoint";

	@Override
	public void configure() throws Exception {

		Processor resultProcessor = new Processor() {
			public void process(Exchange exchange) throws Exception {
				MessageContentsList messageContentsList = (MessageContentsList) exchange
						.getIn().getBody();
				OutputReportIncident outputReportIncident = (OutputReportIncident) messageContentsList
						.get(0);
				log.info("status code: " + outputReportIncident.getCode());
				exchange.getIn().setHeader("statusCode",
						outputReportIncident.getCode());
				exchange.getIn().setBody(outputReportIncident);
			}
		};

		Processor requestProcessor = new Processor() {
			public void process(Exchange exchange) throws Exception {
				MessageContentsList messageContentsList = (MessageContentsList) exchange
						.getIn().getBody();
				InputReportIncident inputReportIncident = (InputReportIncident) messageContentsList
						.get(0);
				exchange.getIn().setHeader("partner",
						inputReportIncident.getSummary());
			}
		};

		OutputReportIncident outOK = new OutputReportIncident();
		outOK.setCode("ok");
		OutputReportIncident outNotOK = new OutputReportIncident();
		outNotOK.setCode("notOK");

		from("cxf:bean:reportIncident").to("direct:toWs");
		from("direct:toWs").routeId("invocationDecision")
				.process(requestProcessor).choice()
				.when(header("partner").isEqualTo("partnerA"))
				.to("direct:partnerA")
				.when(header("partner").isEqualTo("partnerB"))
				.to("direct:partnerB");
		from("direct:partnerA").routeId("invokeA").doTry()
				.to("cxf://" + URL_PART_A + "?serviceClass=" + SERVICE_CLASS)
				.to("direct:wsResponse").doCatch(HTTPException.class)
				.to("direct:errorHandling");
		from("direct:partnerB").to(
				"cxf://" + URL_PART_B + "?serviceClass=" + SERVICE_CLASS).to(
				"direct:wsResponse");
		from("direct:wsResponse").routeId("resultDecision")
				.process(resultProcessor).choice()
				.when(header("statusCode").isEqualTo("ok"))
				.to("direct:statusOK")
				.when(header("statusCode").isEqualTo("notOK"))
				.to("direct:statusNotOK");
		from("direct:statusOK").transform(constant(outNotOK));
		from("direct:statusNotOK").transform(constant(outOK));
	}
}
