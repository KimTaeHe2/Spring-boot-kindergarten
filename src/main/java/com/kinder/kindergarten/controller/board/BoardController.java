package com.kinder.kindergarten.controller.board;

import com.kinder.kindergarten.DTO.board.BoardDTO;
import com.kinder.kindergarten.DTO.board.BoardFileDTO;
import com.kinder.kindergarten.DTO.board.BoardFormDTO;
import com.kinder.kindergarten.DTO.board.CommentsDTO;
import com.kinder.kindergarten.constant.BoardType;
import com.kinder.kindergarten.repository.QueryDSL;
import com.kinder.kindergarten.service.board.BoardService;
import com.kinder.kindergarten.service.board.CommentsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Controller
@Log4j2
@RequestMapping(value="/board")
@RequiredArgsConstructor
public class BoardController {

  private final BoardService boardService;

  private final CommentsService commentsService;
  private final QueryDSL queryDSL;

  @GetMapping(value="/list/{type}")
  public String getBoardsByType(@PathVariable String type,
                                @RequestParam(required = false, defaultValue = "1", value = "page") int pageNum,
                                Model model) {
    try {
      BoardType boardType = BoardType.valueOf(type.toUpperCase(Locale.ROOT));
      pageNum = pageNum == 0 ? 0 : (pageNum - 1);
      Pageable pageable = PageRequest.of(pageNum, 10); // 한 페이지당 10개의 게시글

      Page<BoardDTO> boardDtoPage = boardService.getBoardsByType(boardType, pageable);

      model.addAttribute("boards", boardDtoPage);
      model.addAttribute("currentPage", pageNum);
      model.addAttribute("totalPages", boardDtoPage.getTotalPages());
      // 처리 로직
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid board type");
    }


    return "board/basic";
  }

  @GetMapping(value="/write")
  public String writeBoard(Model model){
    model.addAttribute("boardFormDTO",new BoardFormDTO());
    return "board/write";
  }


  @PostMapping(value="/write")
  public ResponseEntity<?> postWriteBoard(@Valid BoardFormDTO boardFormDTO,
                                          BindingResult bindingResult,
                                          @RequestParam(value = "boardFile", required = false) List<MultipartFile> boardFileList) {
    try {
      if (bindingResult.hasErrors()) {
        return ResponseEntity.badRequest().body("입력값이 올바르지 않습니다.");
      }

      // 테스트용 작성자 설정 (나중에 실제 인증 정보로 변경)
      boardFormDTO.setBoardWriter("테스트작성자");

      String boardId;
      // 파일 존재 여부와 ZIP 생성 옵션에 따른 처리
      if (boardFileList != null && !boardFileList.isEmpty() && !boardFileList.get(0).isEmpty()) {
        if (boardFileList.size() >= 3) {  // 파일이 3개 이상일 경우
          boardService.saveWithZip(boardFormDTO, boardFileList);
        } else {
          boardService.saveBoardWithFile(boardFormDTO, boardFileList);
        }
      } else {
        boardService.saveBoard(boardFormDTO);
      }

      // 게시판 타입에 따른 리다이렉트 URL 설정
      String redirectUrl = switch(boardFormDTO.getBoardType()) {
        case COMMON -> "/board/list/common";
        case DIARY -> "/board/list/diary";
        case RESEARCH -> "/board/list/research";
        case NOTIFICATION -> "/board/list/notification";
      };

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("redirectUrl", redirectUrl);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      log.error("게시글 등록 중 에러 발생: ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("게시글 등록 중 오류가 발생했습니다.");
    }
  }

  @GetMapping(value="/{board_id}")
  public String getBoard(@PathVariable String board_id, Model model, HttpServletRequest request){
    queryDSL.increaseViews(board_id,request);//조회수 1 증가시키기
    BoardDTO boardDTO = boardService.getBoard(board_id);
    log.info("BoardService.getBoard() 실행 : " + boardDTO);
    List<CommentsDTO> comments = commentsService.getCommentsByBoardId(board_id);

    model.addAttribute("boardDTO", boardDTO);
    model.addAttribute("comments", comments);
    return "board/get";
  }

  //파일다운로드
  @GetMapping("/download/{fileId}")
  public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
    try {
      BoardFileDTO fileDTO = boardService.getFile(fileId);
      Path filePath = Paths.get(fileDTO.getFilePath());
      Resource resource = new UrlResource(filePath.toUri());

      if (resource.exists() || resource.isReadable()) {
        // UTF-8로 파일명 인코딩
        String encodedFileName = URLEncoder.encode(fileDTO.getOrignalName(), StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");  // 공백 문자 처리

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8")
                .body(resource);
      } else {
        throw new RuntimeException("파일을 찾을 수 없습니다.");
      }
    } catch (MalformedURLException | UnsupportedEncodingException e) {
      throw new RuntimeException("파일 다운로드 중 오류가 발생했습니다.", e);
    }
  }


  @GetMapping(value="/modify/{boardId}")
  public String modifyBoard(@PathVariable String boardId, Model model) {
    try {
      BoardDTO boardDTO = boardService.getBoard(boardId);
      BoardFormDTO boardFormDTO = new BoardFormDTO();

      // BoardDTO -> BoardFormDTO 변환
      boardFormDTO.setBoardId(boardDTO.getBoardId());
      boardFormDTO.setBoardTitle(boardDTO.getBoardTitle());
      boardFormDTO.setBoardContents(boardDTO.getBoardContents());
      boardFormDTO.setBoardType(boardDTO.getBoardType());
      boardFormDTO.setBoardWriter(boardDTO.getBoardWriter());
      boardFormDTO.setBoardFileList(boardDTO.getBoardFileList());

      model.addAttribute("boardFormDTO", boardFormDTO);
      return "board/write";
    } catch (Exception e) {
      model.addAttribute("errorMessage", "게시글을 불러오는 중 에러가 발생했습니다.");
      return "redirect:/board/" + boardId;
    }
  }

  @PostMapping(value="/modify/{boardId}")
  public String updateBoard(@Valid BoardFormDTO boardFormDTO, BindingResult bindingResult,
                            @PathVariable String boardId,
                            @RequestParam(value = "boardFile", required = false) List<MultipartFile> boardFileList,
                            Model model) {
    if (bindingResult.hasErrors()) {
      return "board/write";
    }

    try {
      if (boardFileList != null && !boardFileList.isEmpty() && !boardFileList.get(0).isEmpty()) {
        boardService.updateBoardWithFile(boardId, boardFormDTO, boardFileList);
      } else {
        boardService.updateBoard(boardId, boardFormDTO);
      }
      return "redirect:/board/" + boardId;
    } catch (Exception e) {
      model.addAttribute("errorMessage", "게시글 수정 중 에러가 발생했습니다.");
      return "board/write";
    }
  }
  @DeleteMapping("/delete/{boardId}")
  public ResponseEntity<?> deleteBoard(@PathVariable String boardId,
                                       Authentication authentication) {
    try {
      BoardDTO board = boardService.getBoard(boardId);

      // 작성자와 로그인한 사용자가 같은지 확인
      if (!board.getBoardWriter().equals(authentication.getName())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("삭제 권한이 없습니다.");
      }

      boardService.deleteBoard(boardId);
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body("삭제 중 오류가 발생했습니다.");
    }
  }

//검색
  @GetMapping("/search")
  public String searchBoards(@RequestParam String keyword,
                             @RequestParam(required = false, defaultValue = "0") int page,
                             Model model) {
    page = page == 0 ? 0 : (page - 1);
    Pageable pageable = PageRequest.of(page, 10);

    Page<BoardDTO> searchResults = boardService.searchBoards(keyword, pageable);

    model.addAttribute("boards", searchResults);
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", searchResults.getTotalPages());
    model.addAttribute("keyword", keyword);

    return "board/basic";
  }


}//class end
