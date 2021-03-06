package orders

import orders.client.MailClient
import orders.controller.OrdersController
import orders.controller.OrdersController.Companion.CALCULATIONS
import orders.controller.OrdersController.Companion.ORDERS
import orders.controller.OrdersController.Companion.USER_ID
import orders.service.KotlinProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.ConnectException
import java.nio.charset.Charset

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = [ OrdersApplication::class]
)
@AutoConfigureMockMvc
class OrdersControllerTest(@Autowired val mockMvc: MockMvc) {

	@MockBean
	private lateinit var mailClient: MailClient

	@MockBean
	private lateinit var kotlinProducer: KotlinProducer

	@Test
	fun contextLoads() {
		assertThat(OrdersController).isNotNull();
	}

	@Test
	fun `Must return normal calculations`() {

		val json = "[\"orange\", \"apple\", \"apple\", \"apple\"]"
		val response = mockMvc
				.perform(get(CALCULATIONS).header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		println(response.andReturn().response.getContentAsString(Charset.defaultCharset()))
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), "{\"totalCost\":\"\$1.45\"}")
	}

	@Test
	fun `Must return normal calculations with offers`() {

		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\"]"
		val response = mockMvc
				.perform(get(CALCULATIONS).header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		println(response.andReturn().response.getContentAsString(Charset.defaultCharset()))
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), "{\"totalCost\":\"\$1.1\"}")
	}

	@Test
	fun `Must return normal calculations with offers with elements that are not present`() {

		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\", \"cucumber\"]"
		val response = mockMvc
				.perform(get(CALCULATIONS).header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		println(response.andReturn().response.getContentAsString(Charset.defaultCharset()))
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), "{\"totalCost\":\"\$1.1\"}")
	}

	@Test
	fun `Must return normal calculations with empty body`() {

		val json = "[]"
		val response = mockMvc
				.perform(get(CALCULATIONS).header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		println(response.andReturn().response.getContentAsString(Charset.defaultCharset()))
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), "{\"totalCost\":\"\$0.0\"}")
	}

	@Test
	fun `Must throw exception without user_id`() {

		val json = "[]"
		val response = mockMvc
				.perform(get(CALCULATIONS).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().is4xxClientError)
				.andDo(print())
				.andReturn()
	}

	@Test
	fun `Must throw exception with no body`() {

		val response = mockMvc
				.perform(get(CALCULATIONS).header(USER_ID, USER_ID))
		response
				.andExpect(status().is4xxClientError)
				.andDo(print())
				.andReturn()
	}

	@Test
	fun `Must place order`() {
		`when`(mailClient.sendMessage(
				anyString(), anyString(), anyBoolean(), anyString(), anyString())).thenReturn("Email sent successfully")
		doNothing().`when`(kotlinProducer).send(anyString())
		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\", \"cucumber\"]"
		val response = mockMvc
				.perform(post("$ORDERS?mailAddress=my.address@gmail.com").header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		val expectedResult = "Dear USER_ID! You placed order that contains [{orange=3, apple=2}] and costs \$1.1. We sent you details to my.address@gmail.com"
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), expectedResult)
	}

	@Test
	fun `Must place order without sending message`() {
		`when`(mailClient.sendMessage(
				anyString(), anyString(), anyBoolean(), anyString(), anyString())).thenReturn("Email wasn't sent")
		`when`(kotlinProducer.send(anyString())).thenThrow(RuntimeException())
		doNothing().`when`(kotlinProducer).send(anyString())
		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\", \"cucumber\"]"
		val response = mockMvc
				.perform(post("$ORDERS?mailAddress=my.address@gmail.com").header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		// this result will work only if there is no broker present
		// val expectedResult = "Dear USER_ID! You placed order that contains [{orange=3, apple=2}] and costs \$1.1. Thank you!"
		val expectedResult = "Dear USER_ID! You placed order that contains [{orange=3, apple=2}] and costs \$1.1. We sent you details to my.address@gmail.com"
		assertEquals(expectedResult, response.andReturn().response.getContentAsString(Charset.defaultCharset()))

	}

	@Test
	fun `Must place order without sending message whn sender service is down`() {
		`when`(mailClient.sendMessage(
				anyString(), anyString(), anyBoolean(), anyString(), anyString())).thenThrow(ConnectException::class.java)
		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\", \"cucumber\"]"
		val response = mockMvc
				.perform(post("$ORDERS?mailAddress=my.address@gmail.com").header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		val expectedResult = "These goods are out of stock. Please place another order."
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), expectedResult)
	}

	@Test
	fun `Must not place order with out of stock`() {
		`when`(mailClient.sendMessage(
				anyString(), anyString(), anyBoolean(), anyString(), anyString())).thenReturn("Email wasn't sent")
		val json = "[\"orange\", \"apple\", \"apple\", \"orange\", \"orange\", \"cucumber\", \"apple\", \"apple\", \"apple\"" +
				", \"apple\", \"apple\", \"apple\", \"apple\", \"apple\"]"
		val response = mockMvc
				.perform(post("$ORDERS?mailAddress=my.address@gmail.com").header(USER_ID, USER_ID).contentType(MediaType.APPLICATION_JSON)
						.content(json))
		response
				.andExpect(status().isOk())
				.andDo(print())
				.andReturn()
		val expectedResult = "These goods are out of stock. Please place another order."
		assertEquals(response.andReturn().response.getContentAsString(Charset.defaultCharset()), expectedResult)
	}
}
