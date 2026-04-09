export interface Question {
  id?: string;
  word: string;
  definition: string;
  length: number;
}

export interface GameState {
  currentQuestionIndex: number;
  score: number;
  timeLeft: number;
  revealedLetters: number[];
  isTimerRunning: boolean;
  isGameOver: boolean;
  isAnswering: boolean;
  isAnswerTimerRunning: boolean;
  answerTimeLeft: number;
  totalScore: number;
}
