package net.softsociety.Team4GroupWare.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import net.softsociety.Team4GroupWare.domain.AttachedFile;
import net.softsociety.Team4GroupWare.domain.Company;
import net.softsociety.Team4GroupWare.domain.DraftApprover;
import net.softsociety.Team4GroupWare.domain.Email;
import net.softsociety.Team4GroupWare.domain.Employee;
import net.softsociety.Team4GroupWare.domain.MailProcess;
import net.softsociety.Team4GroupWare.domain.Mailinfo;
import net.softsociety.Team4GroupWare.service.AdminService;
import net.softsociety.Team4GroupWare.service.DraftService;
import net.softsociety.Team4GroupWare.service.EmailService;
import net.softsociety.Team4GroupWare.service.EmployeeService;
import net.softsociety.Team4GroupWare.util.FileService;


@Slf4j
@Controller
@RequestMapping("mailbox")
public class EmailController {

	// 메일 서비스 선언
	@Autowired
	EmailService emailservice;
	
	// 기안 서비스 선언
	@Autowired
	DraftService draftservice;
		
	// 관리자 서비스 선언
	@Autowired
	AdminService adminservice;
	
	// 회원 서비스 선언
	@Autowired
	EmployeeService employservice;
	
	

	//첨부파일 저장할 경로
	@Value("${spring.servlet.multipart.location}")
	String uploadMailPath;
	
	
	/*------------------------------ 메일함 페이지 나누기 ------------------------------*/
	
	
	//게시판 목록의 페이지당 글 수
	@Value("${user.board.page}")
	int countPerPage;
	
	//게시판 목록의 페이지 이동 링크 수 
	@Value("${user.board.group}")
	int pagePerGroup;	
	
	/*------------------------------ 조직도 관련 ------------------------------*/

	//조직도 불러오는 ajax 컨트롤러
	@ResponseBody
	@PostMapping("readOrg")
	public JSONArray readOrg(@AuthenticationPrincipal UserDetails user) {
		//회사코드, 관리자 내용 가져오기
		Employee admin = adminservice.readAdmin(user.getUsername());
		Company company = adminservice.readCompany(admin.getCompany_code());
		
		JSONArray json = adminservice.readOrg(company);
		
		return json;
	}
	
	//조직도 내 사원 목록 불러오는 ajax 컨트롤러
	@ResponseBody
	@PostMapping("searchEmployee")
	public ArrayList<Employee> searchEmployee(@AuthenticationPrincipal UserDetails user, String organization){
		Employee employee = adminservice.readAdmin(user.getUsername());
		Company company = adminservice.readCompany(employee.getCompany_code());
		
		String realOrg = organization.substring(company.getCompany_name().length()+2);
		
		log.debug("가져온 팀명 : {}", realOrg);

		employee.setOrganization(realOrg);
		
		ArrayList<Employee> empList = adminservice.findByOrganization(employee);
		
		return empList;
	}
	
	//결재선 추가
	@ResponseBody
	@PostMapping("addApproval")
	public int addApproval(DraftApprover approval) {
		int result = 0;
		
		log.debug("가져온 결재선 : {}", approval);
		
		return result;
	}
	
	
	
	//메일 쓰기 페이지로 이동
	@GetMapping("/write")
	public String writeDdraft(@AuthenticationPrincipal UserDetails user, Model model) {
		// 회사코드, 관리자 내용 가져오기
		Employee employee = draftservice.readEmployee(user.getUsername());
		Company company = adminservice.readCompany(employee.getCompany_code());
		JSONArray json = adminservice.readOrg(company);
				
		ArrayList<Employee> empList = adminservice.employeeList(employee);
				
		for(int i = 0; i < empList.size(); i++) {
			if(empList.get(i).getRole_name().equals("ROLE_ADMIN")) {
				empList.remove(i);
			}
		}
				
		model.addAttribute("employee", employee);
		model.addAttribute("company", company);
		model.addAttribute("json", json);
		model.addAttribute("empList",empList);
		
		return "mailbox/write";
	}

	
	/*------------------------------ 메일 쓰기 ------------------------------*/
	//작성된 메일 내용 받아오기
	@PostMapping("write")
	public String sendMail(Email email
			, AttachedFile file
			, MultipartFile upload
			, MailProcess mail_process
			, @AuthenticationPrincipal UserDetails user
			) throws Exception {

		//회사코드, 로그인 한 사람 내용 가져오기
		Employee employee = employservice.getEmployeeById(user.getUsername());
		
		email.setEmail_sender(employee.getEmployee_email());
		email.setCompany_code(employee.getCompany_code()); 
		
		
		log.debug("Employee 받은 내용: {}", employee);
		log.debug("email 받은 내용: {}", email);
		log.debug("mail_process 받은 내용: {}", mail_process);
		
		int emailresult = emailservice.insertMailTotal(email);
		
		file.setCompany_code(employee.getCompany_code());
		file.setEmployee_code(employee.getEmployee_code());
		file.setEmployee_id(employee.getEmployee_id());
		file.setEmployee_name(employee.getEmployee_name());
		log.debug("file : {}", file);

		/*---------------------첨부파일 정보 입력--------------------------*/
		
		if (upload != null && !upload.isEmpty() && upload.getSize()!=0) {

			try {
				String absoluteUploadPath = new ClassPathResource(uploadMailPath).getFile().getAbsolutePath();
				log.debug("absoluteUploadPath : {}", absoluteUploadPath);
				String savedfile = FileService.saveFile(upload, absoluteUploadPath);

				//확장자 추출
				String originalFilename = upload.getOriginalFilename();
				int lastIndex = originalFilename.lastIndexOf('.');
				String ext = lastIndex == -1 ? "" : originalFilename.substring(lastIndex);

				file.setAttached_file_ext(ext);
				file.setAttached_file_origin_name(originalFilename);
				file.setAttached_file_save_name(savedfile);

				file.setDocument_code(email.getEmail_code());
				log.debug("첨부파일 저장 전 파일 데이터 : {}", file);
				
				int result = emailservice.insertMailAtteched(file);
				if(result == 1) {
					log.debug("첨부파일 저장 성공 후 파일 데이터 : {}", file);

				}
			} catch (IOException e) {
				log.debug(e.getMessage());
			}
		}
		//성공시, 완료페이지 
		return "redirect:/mailbox/sentMailbox";
	}

	
	/*------------------------------ 보낸 메일함 ------------------------------*/
	@GetMapping("sentMailbox")
	public String sentMailbox(Model model
						, @RequestParam(name = "page", defaultValue="1")int page
						, @AuthenticationPrincipal UserDetails user
						) {
		Employee employee = adminservice.readAdmin(user.getUsername());
		String email_sender = employee.getEmployee_email();
		
		ArrayList<Mailinfo> mailinfo = emailservice.selectSentmail(email_sender); 
		
		//모델에 담아서 html로 보내주기
		model.addAttribute("mailinfo", mailinfo);
		model.addAttribute("employee", employee);
		
		return "mailbox/sentMailbox";
	}
	
	@GetMapping("email-read")
	public String readSentMail() {
		return "mailbox/email-read";
	}
	
	/*------------------------------ 보낸 메일함 - 메일 1개 읽기 ------------------------------*/
	@GetMapping("sentMailboxOne")
	public String readSnetmail(
		String email_code
		, Model model
		, @AuthenticationPrincipal UserDetails user
			) {
		log.debug("VIEW에서 넘어온 메일 번호 : {}", email_code);
		
		Employee employee = adminservice.readAdmin(user.getUsername());
		Mailinfo mailinfo = emailservice.selectOne(email_code);
		AttachedFile attachedfile = emailservice.MailAttachedfile(email_code);
		Employee employee_receiver= employservice.getEmployeeById(user.getUsername());
	    Employee employee_sender  = employservice.getEmployeeByEmail(mailinfo.getEmail_sender());

		
		log.debug("DB에서 넘어온 메일 정보 : {} ", mailinfo);
		log.debug("DB에서 넘어온 첨부파일 정보 : {} ", attachedfile);
	
		model.addAttribute("employee", employee);
		model.addAttribute("mailinfo", mailinfo);
		model.addAttribute("attachedfile", attachedfile);
		model.addAttribute("employee_sender", employee_sender);
	    model.addAttribute("employee_receiver", employee_receiver);
		
		//결과를 모델에 담아서 HTML에서 출력
		return "/mailbox/sentMailboxOne";
	}
	
	/*------------------------------ 전체 메일함 ------------------------------*/

	@GetMapping("readAll")
	public String readAll(Model model
			,@AuthenticationPrincipal UserDetails user
			) {
		Employee employee = adminservice.readAdmin(user.getUsername());
		String email_receiver = employee.getEmployee_email();
		
		ArrayList<Mailinfo> mailinfo = emailservice.readAllmail(email_receiver); 

		System.out.println(mailinfo);
		//모델에 담아서 html로 보내주기
		model.addAttribute("mailinfo", mailinfo);
		model.addAttribute("employee", employee);
		
		return "mailbox/readAll";
	}
	
	/*------------------------------ 전체 메일함 - 메일 읽기 ------------------------------*/
	@GetMapping("readOne")
	public String read(
		String email_code
		, Model model
		, @AuthenticationPrincipal UserDetails user) {
log.debug("VIEW에서 넘어온 메일 번호 : {}", email_code);
		
		Employee employee = adminservice.readAdmin(user.getUsername());
		Mailinfo mailinfo = emailservice.selectOne(email_code);
		AttachedFile attachedfile = emailservice.MailAttachedfile(email_code);
		Employee employee_receiver= employservice.getEmployeeById(user.getUsername());
	    Employee employee_sender  = employservice.getEmployeeByEmail(mailinfo.getEmail_sender());

		
		log.debug("DB에서 넘어온 메일 정보 : {} ", mailinfo);
		log.debug("DB에서 넘어온 첨부파일 정보 : {} ", attachedfile);
	
		model.addAttribute("employee", employee);
		model.addAttribute("mailinfo", mailinfo);
		model.addAttribute("attachedfile", attachedfile);
		model.addAttribute("employee_sender", employee_sender);
	    model.addAttribute("employee_receiver", employee_receiver);
		
		return "mailbox/readOne";
	}
	
	/*------------------------------ 전체 메일함 - 메일 1개 읽기  첨부파일 다운로드------------------------------*/
	@RequestMapping(value = "download", method = RequestMethod.GET)
	public String fileDownload(String email_code, Model model, HttpServletResponse response) {
		
		AttachedFile attachedfile = emailservice.MailAttachedfile(email_code);

		//파일의 원래 이름
		String originalfile = new String(attachedfile.getAttached_file_origin_name());

		try { 
			response.setHeader("Content-Disposition", " attachment;filename="+ URLEncoder.encode(originalfile, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		//저장된 파일 경로
		String fullPath = uploadMailPath + "/" + attachedfile.getAttached_file_save_name();
		
		//서버의 파일을 읽을 입력 스트림과 클라이언트에게 전달할 출력스트림
		FileInputStream filein = null;
		ServletOutputStream fileout = null;
		
		try {
			filein = new FileInputStream(fullPath);
			fileout = response.getOutputStream();
			
			//Spring의 파일 관련 유틸 이용하여 출력
			FileCopyUtils.copy(filein, fileout);
			
			filein.close();
			fileout.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	
}