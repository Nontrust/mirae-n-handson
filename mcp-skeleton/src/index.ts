// 문항 은행 MCP 서버 골격 (TypeScript SDK v2 · stdio)
//
// stdio 서버에서는 stdout 이 곧 프로토콜 채널이다.
// 로그는 console.error 만 쓴다(stdout 으로 찍는 로그 함수는 절대 쓰지 않는다).
import { McpServer } from '@modelcontextprotocol/server';
import { StdioServerTransport } from '@modelcontextprotocol/server/stdio';
import * as z from 'zod/v4';
import { searchItems } from './itemApi.js';

const server = new McpServer({ name: 'item-bank', version: '1.0.0' });

// 예시 도구: 서버가 살아 있는지 확인한다. 입력 없음.
server.registerTool(
  'ping',
  {
    description: '서버 연결 확인용 예시 도구. "pong" 을 돌려준다.'
  },
  async () => ({
    content: [{ type: 'text', text: 'pong' }]
  })
);

// ================================================================
// 여기에 도구를 등록합니다
//
//   server.registerTool('도구_이름', { description, inputSchema }, handler)
//
// - inputSchema 는 z.object({...}) 전체 스키마로 넘긴다 (import * as z from 'zod/v4')
// - 감쌀 API 는 ./itemApi.js 에 있다 (ESM 이라 확장자를 .js 로 적는다)
// - import 문도 이 자리에 함께 붙여 넣어도 된다
// ================================================================

// 조회 전용 도구: 문항 은행에서 문항을 검색한다. 쓰기 기능은 없다.
server.registerTool(
  'search_items',
  {
    description:
      '초등 수학 문항 은행에서 문항을 검색한다. 사용자가 특정 주제·키워드(예: "통분", "대분수", "문장제")의 문항을 찾거나, ' +
      '단원·난이도별로 문항 후보를 보고 싶을 때 사용한다. 문항 id · 단원 · 난이도 · 태그 · 본문을 돌려준다. ' +
      '문항을 수정하거나 태그를 바꾸는 용도로는 쓸 수 없다(조회 전용).',
    inputSchema: z.object({
      keyword: z.string().describe('검색어. 문항 본문 또는 태그에 포함된 단어 (예: "통분", "문장제")'),
      unit: z.string().optional().describe('단원 코드(예: "M5-1") 또는 단원 이름 일부(예: "분수의 곱셈"). 생략하면 전체 단원'),
      difficulty: z.enum(['하', '중', '상']).optional().describe('난이도: "하", "중", "상" 중 하나. 생략하면 전체 난이도'),
      limit: z.number().int().min(1).max(20).default(5).describe('최대 결과 건수 (1~20, 기본 5)')
    })
  },
  async ({ keyword, unit, difficulty, limit }) => {
    const conditions = { keyword, unit, difficulty, limit };
    try {
      const results = await searchItems(conditions);
      console.error(`search_items: ${results.length}건`);
      if (results.length === 0) {
        return {
          content: [{ type: 'text', text: JSON.stringify({ message: '검색 결과 없음', conditions }, null, 2) }]
        };
      }
      return {
        content: [{ type: 'text', text: JSON.stringify(results, null, 2) }]
      };
    } catch (error) {
      console.error('search_items failed:', error);
      return {
        isError: true,
        content: [{ type: 'text', text: '문항 검색 중 오류가 발생했습니다.' }]
      };
    }
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('item-bank MCP server running on stdio');
}

main().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
